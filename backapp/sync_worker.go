package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

var (
	GlobalSyncEngine *SyncEngine
	syncEngineOnce   sync.Once
)

type SyncEngine struct {
	cloudBaseURL    string
	branchID        string
	deviceID        string
	httpClient      *http.Client
	stopChan        chan struct{}
	triggerChan     chan struct{}
	isOnline        bool
	lastSyncedAt    int64
	lastPullAt      int64
	lastSyncError   string
	mu              sync.RWMutex
	isSyncing       bool
}

func InitSyncEngine() *SyncEngine {
	syncEngineOnce.Do(func() {
		cloudURL := os.Getenv("CLOUD_API_URL")
		if cloudURL == "" {
			cloudURL = "http://localhost:8080/api/v1"
		}

		branchID := os.Getenv("POS_BRANCH_ID")
		deviceID := os.Getenv("POS_DEVICE_ID")

		if DB != nil {
			if ident, err := GetDeviceIdentity(DB); err == nil && ident != nil {
				if branchID == "" {
					branchID = ident.BranchID
				}
				if deviceID == "" {
					deviceID = ident.DeviceID
				}
				if cloudURL == "" && ident.CloudApiUrl != "" {
					cloudURL = ident.CloudApiUrl
				}
			}
		}

		if branchID == "" {
			branchID = "branch-001"
		}
		if deviceID == "" {
			deviceID = "pos-edge-01"
		}

		GlobalSyncEngine = &SyncEngine{
			cloudBaseURL: cloudURL,
			branchID:     branchID,
			deviceID:     deviceID,
			httpClient: &http.Client{
				Timeout: 10 * time.Second,
			},
			stopChan:    make(chan struct{}),
			triggerChan: make(chan struct{}, 10),
			isOnline:    false,
		}
	})
	return GlobalSyncEngine
}

// EnqueueSync adds an event into the local SQLite sync_queue table
func EnqueueSync(entityType, entityId, action string, payload any) error {
	payloadBytes, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("failed to marshal sync payload: %w", err)
	}

	queueID := fmt.Sprintf("sq-%d-%s", time.Now().UnixNano(), entityId)
	createdAt := NowMillis()

	query := `
	INSERT INTO sync_queue (queue_id, entity_type, entity_id, action, payload_json, status, retry_count, created_at)
	VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?)`

	_, err = DB.Exec(query, queueID, entityType, entityId, action, string(payloadBytes), createdAt)
	if err != nil {
		log.Printf("[SyncQueue] ❌ Error enqueuing sync item: %v", err)
		return err
	}

	log.Printf("[SyncQueue] 📥 Enqueued %s:%s (Action: %s)", entityType, entityId, action)

	// Trigger immediate sync attempt
	if GlobalSyncEngine != nil {
		select {
		case GlobalSyncEngine.triggerChan <- struct{}{}:
		default:
		}
	}

	return nil
}

// Start runs background Goroutine for automatic 2-way sync
func (s *SyncEngine) Start() {
	log.Printf("[SyncEngine] 🚀 Background 2-Way Sync Engine started (Cloud: %s)", s.cloudBaseURL)
	go s.syncLoop()
}

// Stop gracefully shuts down the sync loop
func (s *SyncEngine) Stop() {
	close(s.stopChan)
	log.Println("[SyncEngine] 🛑 Sync Engine stopped")
}

// TriggerSync forces an immediate sync run
func (s *SyncEngine) TriggerSync() {
	select {
	case s.triggerChan <- struct{}{}:
	default:
	}
}

// GetStatus returns current sync telemetry
func (s *SyncEngine) GetStatus() SyncStatusInfo {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var pendingCount int
	var failedCount int
	_ = DB.QueryRow("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'").Scan(&pendingCount)
	_ = DB.QueryRow("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'").Scan(&failedCount)

	return SyncStatusInfo{
		IsOnline:         s.isOnline,
		CloudURL:         s.cloudBaseURL,
		PendingItemCount: pendingCount,
		FailedItemCount:  failedCount,
		LastSyncedAt:     s.lastSyncedAt,
		LastPullAt:       s.lastPullAt,
		LastSyncError:    s.lastSyncError,
	}
}

func (s *SyncEngine) syncLoop() {
	ticker := time.NewTicker(6 * time.Second)
	defer ticker.Stop()

	// Initial immediate sync
	s.performFullSync()

	for {
		select {
		case <-s.stopChan:
			return
		case <-s.triggerChan:
			s.performFullSync()
		case <-ticker.C:
			s.performFullSync()
		}
	}
}

func (s *SyncEngine) performFullSync() {
	s.mu.Lock()
	if s.isSyncing {
		s.mu.Unlock()
		return
	}
	s.isSyncing = true
	s.mu.Unlock()

	defer func() {
		s.mu.Lock()
		s.isSyncing = false
		s.mu.Unlock()
	}()

	// 1. Health check cloud
	isReachable := s.checkCloudHealth()
	s.mu.Lock()
	s.isOnline = isReachable
	s.mu.Unlock()

	if !isReachable {
		return
	}

	// 2. Push pending sales / orders / payments to cloud
	if err := s.pushPendingUpstream(); err != nil {
		s.mu.Lock()
		s.lastSyncError = err.Error()
		s.mu.Unlock()
		log.Printf("[SyncEngine] ⚠️ Push upstream error: %v", err)
	}

	// 3. Pull latest catalog updates from cloud
	if err := s.pullDownstreamUpdates(); err != nil {
		log.Printf("[SyncEngine] ⚠️ Pull downstream error: %v", err)
	}
}

func (s *SyncEngine) checkCloudHealth() bool {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.cloudBaseURL+"/sync/health", nil)
	if err != nil {
		return false
	}

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()

	return resp.StatusCode == http.StatusOK
}

// ── UPSTREAM: Push Local SQLite Orders & Payments to Cloud ──
func (s *SyncEngine) pushPendingUpstream() error {
	rows, err := DB.Query(`
		SELECT queue_id, entity_type, entity_id, action, payload_json, status, retry_count, created_at 
		FROM sync_queue 
		WHERE status = 'PENDING' OR (status = 'FAILED' AND retry_count < 5)
		ORDER BY created_at ASC 
		LIMIT 50`)
	if err != nil {
		return err
	}
	defer rows.Close()

	var pendingItems []SyncQueueItem
	for rows.Next() {
		var item SyncQueueItem
		if err := rows.Scan(&item.QueueID, &item.EntityType, &item.EntityID, &item.Action, &item.PayloadJSON, &item.Status, &item.RetryCount, &item.CreatedAt); err == nil {
			pendingItems = append(pendingItems, item)
		}
	}

	if len(pendingItems) == 0 {
		return nil
	}

	log.Printf("[SyncEngine] 📤 Pushing %d pending items to Cloud...", len(pendingItems))

	pushPayload := SyncPushBatchRequest{
		BranchID: s.branchID,
		DeviceID: s.deviceID,
		Items:    pendingItems,
	}

	payloadBytes, err := json.Marshal(pushPayload)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.cloudBaseURL+"/sync/push", bytes.NewReader(payloadBytes))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Branch-ID", s.branchID)
	req.Header.Set("X-Device-ID", s.deviceID)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		s.markItemsRetry(pendingItems, err.Error())
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		errMsg := fmt.Sprintf("Cloud returned HTTP %d", resp.StatusCode)
		s.markItemsRetry(pendingItems, errMsg)
		return fmt.Errorf("%s", errMsg)
	}

	var batchResp ApiResponse[SyncPushBatchResponse]
	if err := json.NewDecoder(resp.Body).Decode(&batchResp); err != nil {
		return err
	}

	now := NowMillis()
	// Mark success items
	for _, id := range batchResp.Data.SuccessIds {
		_, _ = DB.Exec("UPDATE sync_queue SET status = 'SYNCED', synced_at = ? WHERE queue_id = ?", now, id)
	}

	s.mu.Lock()
	s.lastSyncedAt = now
	s.lastSyncError = ""
	s.mu.Unlock()

	log.Printf("[SyncEngine] ✅ Successfully synced %d items to Cloud", len(batchResp.Data.SuccessIds))
	return nil
}

func (s *SyncEngine) markItemsRetry(items []SyncQueueItem, errMsg string) {
	for _, it := range items {
		_, _ = DB.Exec(`
			UPDATE sync_queue 
			SET status = CASE WHEN retry_count >= 4 THEN 'FAILED' ELSE 'PENDING' END,
			    retry_count = retry_count + 1,
			    error_message = ?
			WHERE queue_id = ?`, errMsg, it.QueueID)
	}
}

// ── DOWNSTREAM: Pull Master Catalog & Tables from Cloud into SQLite ──
func (s *SyncEngine) pullDownstreamUpdates() error {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	url := fmt.Sprintf("%s/sync/pull?branchId=%s&since=%d", s.cloudBaseURL, s.branchID, s.lastPullAt)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Cloud pull returned status %d", resp.StatusCode)
	}

	var pullResp ApiResponse[SyncPullResponse]
	if err := json.NewDecoder(resp.Body).Decode(&pullResp); err != nil {
		return err
	}

	data := pullResp.Data
	if data.Branch != nil || len(data.Categories) > 0 || len(data.MenuItems) > 0 || len(data.BuffetTiers) > 0 || len(data.Zones) > 0 || len(data.Tables) > 0 || len(data.Promotions) > 0 || len(data.Users) > 0 {
		s.upsertMasterCatalog(data)
	}

	now := NowMillis()
	s.mu.Lock()
	s.lastPullAt = now
	s.mu.Unlock()

	return nil
}

func (s *SyncEngine) upsertMasterCatalog(data SyncPullResponse) {
	tx, err := DB.Begin()
	if err != nil {
		log.Printf("[SyncEngine] ❌ Failed to begin transaction for master catalog: %v", err)
		return
	}
	defer tx.Rollback()

	// 1. Branch Details
	if data.Branch != nil && data.Branch.BranchID != "" {
		_, _ = tx.Exec(`
			INSERT INTO cached_branches (branch_id, company_id, name, code, business_day_close_time, tax_rate, service_charge_rate)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(branch_id) DO UPDATE SET
				company_id = excluded.company_id,
				name = excluded.name,
				code = excluded.code,
				business_day_close_time = excluded.business_day_close_time,
				tax_rate = excluded.tax_rate,
				service_charge_rate = excluded.service_charge_rate`,
			data.Branch.BranchID, data.Branch.CompanyID, data.Branch.Name, data.Branch.Code,
			data.Branch.BusinessDayCloseTime, data.Branch.TaxRate, data.Branch.ServiceChargeRate)
	}

	// 2. Zones
	for _, z := range data.Zones {
		isAct := 1
		if !z.IsActive {
			isAct = 0
		}
		_, _ = tx.Exec(`
			INSERT INTO room_zones (zone_id, branch_id, name, zone_type, sort_order, is_active)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT(zone_id) DO UPDATE SET
				branch_id = excluded.branch_id,
				name = excluded.name,
				zone_type = excluded.zone_type,
				sort_order = excluded.sort_order,
				is_active = excluded.is_active`,
			z.ZoneID, z.BranchID, z.Name, z.ZoneType, z.SortOrder, isAct)
	}

	// 3. Tables
	for _, t := range data.Tables {
		isAct := 1
		if !t.IsActive {
			isAct = 0
		}
		_, _ = tx.Exec(`
			INSERT INTO room_tables (table_id, branch_id, zone_id, table_type_id, name_number, capacity, status, is_active)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(table_id) DO UPDATE SET
				branch_id = excluded.branch_id,
				zone_id = excluded.zone_id,
				table_type_id = excluded.table_type_id,
				name_number = excluded.name_number,
				capacity = excluded.capacity,
				is_active = excluded.is_active`,
			t.TableID, t.BranchID, t.ZoneID, t.TableTypeID, t.NameNumber, t.Capacity, t.Status, isAct)
	}

	// 4. Categories
	for _, c := range data.Categories {
		isAct := 1
		if !c.IsActive {
			isAct = 0
		}
		_, _ = tx.Exec(`
			INSERT INTO room_menu_categories (category_id, branch_id, name, description, sort_order, is_active)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT(category_id) DO UPDATE SET
				branch_id = excluded.branch_id,
				name = excluded.name,
				description = excluded.description,
				sort_order = excluded.sort_order,
				is_active = excluded.is_active`,
			c.CategoryID, c.BranchID, c.Name, c.Description, c.SortOrder, isAct)
	}

	// 5. Menu Items
	for _, m := range data.MenuItems {
		isAct := 1
		if !m.IsActive {
			isAct = 0
		}
		allowDec := 0
		if m.AllowDecimal {
			allowDec = 1
		}
		_, _ = tx.Exec(`
			INSERT INTO room_menu_items (item_id, branch_id, category_id, name, description, sku, base_price, availability, image_url, sort_order, is_active, allow_decimal, unit_name)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(item_id) DO UPDATE SET
				branch_id = excluded.branch_id,
				category_id = excluded.category_id,
				name = excluded.name,
				description = excluded.description,
				sku = excluded.sku,
				base_price = excluded.base_price,
				availability = excluded.availability,
				image_url = excluded.image_url,
				sort_order = excluded.sort_order,
				is_active = excluded.is_active,
				allow_decimal = excluded.allow_decimal,
				unit_name = excluded.unit_name`,
			m.ItemID, m.BranchID, m.CategoryID, m.Name, m.Description, m.SKU, m.BasePrice, m.Availability, m.ImageURL, m.SortOrder, isAct, allowDec, m.UnitName)
	}

	// 6. Buffet Tiers
	for _, b := range data.BuffetTiers {
		isAct := 1
		if !b.IsActive {
			isAct = 0
		}
		_, _ = tx.Exec(`
			INSERT INTO room_buffet_tiers (tier_id, promotion_id, name, adult_price, child_price, time_limit_minutes, brand_id, branch_id, is_active)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(tier_id) DO UPDATE SET
				name = excluded.name,
				adult_price = excluded.adult_price,
				child_price = excluded.child_price,
				time_limit_minutes = excluded.time_limit_minutes,
				brand_id = excluded.brand_id,
				branch_id = excluded.branch_id,
				is_active = excluded.is_active`,
			b.TierID, b.PromotionID, b.Name, b.AdultPrice, b.ChildPrice, b.TimeLimitMinutes, b.BrandID, b.BranchID, isAct)

		if len(b.EligibleItemIDs) > 0 {
			_, _ = tx.Exec("DELETE FROM room_buffet_tier_menu_items WHERE buffet_tier_id = ?", b.TierID)
			for _, itm := range b.EligibleItemIDs {
				_, _ = tx.Exec("INSERT OR IGNORE INTO room_buffet_tier_menu_items (buffet_tier_id, menu_item_id) VALUES (?, ?)", b.TierID, itm)
			}
		}
	}

	// 7. Promotions
	for _, p := range data.Promotions {
		isAct := 1
		if !p.IsActive {
			isAct = 0
		}
		_, _ = tx.Exec(`
			INSERT INTO room_promotions (promotion_id, code, name, description, promo_type, priority, is_active, discount_rate, discount_amount, min_order_amount, stacking_policy)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(promotion_id) DO UPDATE SET
				code = excluded.code,
				name = excluded.name,
				description = excluded.description,
				promo_type = excluded.promo_type,
				priority = excluded.priority,
				is_active = excluded.is_active,
				discount_rate = excluded.discount_rate,
				discount_amount = excluded.discount_amount,
				min_order_amount = excluded.min_order_amount,
				stacking_policy = excluded.stacking_policy`,
			p.PromotionID, p.Code, p.Name, p.Description, p.PromoType, p.Priority, isAct, p.DiscountRate, p.DiscountAmount, p.MinOrderAmount, p.StackingPolicy)
	}

	// 8. Cached Users & Permissions
	for _, u := range data.Users {
		isAct := 1
		if !u.IsActive {
			isAct = 0
		}
		_, _ = tx.Exec(`
			INSERT INTO cached_users (user_id, company_id, username, full_name, pin_hash, is_active)
			VALUES (?, ?, ?, ?, ?, ?)
			ON CONFLICT(user_id) DO UPDATE SET
				company_id = excluded.company_id,
				username = excluded.username,
				full_name = excluded.full_name,
				pin_hash = CASE WHEN excluded.pin_hash != '' THEN excluded.pin_hash ELSE cached_users.pin_hash END,
				is_active = excluded.is_active`,
			u.UserID, u.CompanyID, u.Username, u.FullName, u.PinHash, isAct)

		if len(u.Permissions) > 0 {
			_, _ = tx.Exec("DELETE FROM cached_permissions WHERE user_id = ?", u.UserID)
			for _, perm := range u.Permissions {
				_, _ = tx.Exec("INSERT INTO cached_permissions (user_id, permission_code) VALUES (?, ?)", u.UserID, perm)
			}
		}
	}

	if err := tx.Commit(); err != nil {
		log.Printf("[SyncEngine] ❌ Failed to commit master catalog transaction: %v", err)
		return
	}

	log.Printf("[SyncEngine] 📥 Local SQLite updated: %d categories, %d items, %d buffet tiers, %d zones, %d tables, %d promotions, %d users",
		len(data.Categories), len(data.MenuItems), len(data.BuffetTiers), len(data.Zones), len(data.Tables), len(data.Promotions), len(data.Users))
}
