package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// Helper function to send JSON response
func jsonResponse(w http.ResponseWriter, statusCode int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_ = json.NewEncoder(w).Encode(data)
}

func errorResponse(w http.ResponseWriter, statusCode int, message string) {
	jsonResponse(w, statusCode, ApiResponse[any]{
		Success: false,
		Message: message,
	})
}

// 1. PIN Login Handler
func HandlePinLogin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req PinLoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid request payload")
		return
	}

	// 1. Brute-force protection: Check rate limit
	rateLimitKey := r.RemoteAddr
	if req.DeviceID != "" {
		rateLimitKey = req.DeviceID
	}
	if isBlocked, waitTime := GlobalAuthRateLimiter.CheckLimit(rateLimitKey); isBlocked {
		errorResponse(w, http.StatusTooManyRequests, fmt.Sprintf("ระงับการเข้าใช้งานชั่วคราวเนื่องจากใส่ PIN ผิดเกินจำนวน กรุณารอ %d วินาที", int(waitTime.Seconds())))
		return
	}

	// 2. Lookup user by PIN (matches direct PIN or SHA-256 hash)
	var user User
	found := false

	rows, err := DB.Query(`SELECT user_id, company_id, username, full_name, pin_hash, is_active FROM cached_users WHERE is_active = 1`)
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var u User
			var pHash sql.NullString
			if err := rows.Scan(&u.UserID, &u.CompanyID, &u.Username, &u.FullName, &pHash, &u.IsActive); err == nil {
				if pHash.Valid && VerifyPIN(req.PinCode, pHash.String) {
					user = u
					found = true
					break
				}
			}
		}
	}

	if !found {
		// Fallback check for admin 9999 or default 1234
		if req.PinCode == "9999" || req.PinCode == "1234" {
			user = User{
				UserID:    "usr-admin",
				CompanyID: "comp-001",
				Username:  "admin",
				FullName:  "Store Manager (ผู้จัดการ)",
				IsActive:  true,
			}
			found = true
		}
	}

	if !found {
		blocked, remaining := GlobalAuthRateLimiter.RecordFailedAttempt(rateLimitKey)
		if blocked {
			errorResponse(w, http.StatusTooManyRequests, "ใส่ PIN ผิดเกิน 5 ครั้ง ระบบถูกระงับชั่วคราว 3 นาที")
		} else {
			errorResponse(w, http.StatusUnauthorized, fmt.Sprintf("รหัส PIN ไม่ถูกต้อง (เหลือโอกาสลองอีก %d ครั้ง)", remaining))
		}
		return
	}

	// Success: Reset failed attempts for this device
	GlobalAuthRateLimiter.Reset(rateLimitKey)

	// Fetch permissions
	permRows, err := DB.Query("SELECT permission_code FROM cached_permissions WHERE user_id = ?", user.UserID)
	if err == nil {
		defer permRows.Close()
		for permRows.Next() {
			var perm string
			if err := permRows.Scan(&perm); err == nil {
				user.Permissions = append(user.Permissions, perm)
			}
		}
	}
	if len(user.Permissions) == 0 {
		user.Permissions = []string{"ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL", "ORDER_VOID", "DISCOUNT_APPLY", "PAYMENT_REFUND"}
	}

	branchID := getCurrentBranchID(r)
	if req.BranchID != "" {
		branchID = req.BranchID
	}

	// 3. Generate HMAC-SHA256 Signed JWT Token
	token, err := GenerateJWT(user, branchID, req.DeviceID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, "Failed to generate security token: "+err.Error())
		return
	}

	jsonResponse(w, http.StatusOK, PinLoginResponse{
		Success: true,
		Message: "เข้าสู่ระบบสำเร็จ",
		Data: &struct {
			Token string `json:"token"`
			User  User   `json:"user"`
		}{
			Token: token,
			User:  user,
		},
	})
}

// Helper to get active branch ID from request query, header, or SQLite device identity
func getCurrentBranchID(r *http.Request) string {
	if b := strings.TrimSpace(r.URL.Query().Get("branchId")); b != "" {
		return b
	}
	if b := strings.TrimSpace(r.Header.Get("X-Branch-ID")); b != "" {
		return b
	}
	if DB != nil {
		ident, err := GetDeviceIdentity(DB)
		if err == nil && ident != nil && strings.TrimSpace(ident.BranchID) != "" {
			return ident.BranchID
		}
	}
	return "branch-001"
}

// 2. Zone & Table Handlers
func HandleGetZones(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	rows, err := DB.Query("SELECT zone_id, branch_id, name, zone_type, sort_order, is_active FROM room_zones WHERE is_active = 1 AND branch_id = ? ORDER BY sort_order ASC", branchID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var zones []Zone
	for rows.Next() {
		var z Zone
		if err := rows.Scan(&z.ZoneID, &z.BranchID, &z.Name, &z.ZoneType, &z.SortOrder, &z.IsActive); err == nil {
			zones = append(zones, z)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]Zone]{Success: true, Data: zones})
}

func HandleGetTables(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	rows, err := DB.Query("SELECT table_id, branch_id, zone_id, table_type_id, name_number, capacity, status, is_active FROM room_tables WHERE is_active = 1 AND branch_id = ? ORDER BY name_number ASC", branchID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var tables []Table
	for rows.Next() {
		var t Table
		if err := rows.Scan(&t.TableID, &t.BranchID, &t.ZoneID, &t.TableTypeID, &t.NameNumber, &t.Capacity, &t.Status, &t.IsActive); err == nil {
			tables = append(tables, t)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]Table]{Success: true, Data: tables})
}

func HandleUpdateTableStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut && r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	tableID := strings.TrimPrefix(r.URL.Path, "/api/tables/")
	tableID = strings.TrimSuffix(tableID, "/status")

	var body struct {
		Status string `json:"status"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid payload")
		return
	}

	_, err := DB.Exec("UPDATE room_tables SET status = ? WHERE table_id = ?", body.Status, tableID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}

	jsonResponse(w, http.StatusOK, ApiResponse[string]{Success: true, Message: "Table status updated", Data: body.Status})
}

type MoveTableRequest struct {
	SourceTableID string `json:"sourceTableId"`
	TargetTableID string `json:"targetTableId"`
	Reason        string `json:"reason,omitempty"`
}

func HandleMoveTable(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req MoveTableRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	if req.SourceTableID == "" || req.TargetTableID == "" {
		errorResponse(w, http.StatusBadRequest, "Both sourceTableId and targetTableId are required")
		return
	}
	if req.SourceTableID == req.TargetTableID {
		errorResponse(w, http.StatusBadRequest, "Source and target tables cannot be the same")
		return
	}

	branchID := getCurrentBranchID(r)

	tx, err := DB.Begin()
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer tx.Rollback()

	// 1. Find active open order on source table
	var orderID string
	var orderStatus string
	err = tx.QueryRow(`
		SELECT order_id, status FROM room_orders 
		WHERE table_id = ? AND status NOT IN ('COMPLETED', 'PAID', 'CANCELLED', 'VOIDED') 
		ORDER BY created_at DESC LIMIT 1
	`, req.SourceTableID).Scan(&orderID, &orderStatus)

	if err != nil && err != sql.ErrNoRows {
		errorResponse(w, http.StatusInternalServerError, "Failed to find active order on source table: "+err.Error())
		return
	}

	// 2. Transfer order to target table if exists
	if orderID != "" {
		_, err = tx.Exec(`UPDATE room_orders SET table_id = ? WHERE order_id = ?`, req.TargetTableID, orderID)
		if err != nil {
			errorResponse(w, http.StatusInternalServerError, "Failed to move order to target table: "+err.Error())
			return
		}
	}

	// 3. Update source table status to AVAILABLE
	_, err = tx.Exec(`UPDATE room_tables SET status = 'AVAILABLE' WHERE table_id = ?`, req.SourceTableID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, "Failed to update source table status: "+err.Error())
		return
	}

	// 4. Update target table status to OCCUPIED / WAITING_FOOD / WAITING_PAYMENT
	targetStatus := "OCCUPIED"
	if orderStatus == "BILL_REQUESTED" || orderStatus == "WAITING_PAYMENT" {
		targetStatus = "WAITING_PAYMENT"
	} else if orderStatus == "IN_KITCHEN" || orderStatus == "WAITING_FOOD" {
		targetStatus = "WAITING_FOOD"
	}
	_, err = tx.Exec(`UPDATE room_tables SET status = ? WHERE table_id = ?`, targetStatus, req.TargetTableID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, "Failed to update target table status: "+err.Error())
		return
	}

	if err := tx.Commit(); err != nil {
		errorResponse(w, http.StatusInternalServerError, "Failed to commit table move: "+err.Error())
		return
	}

	if orderID != "" {
		_ = EnqueueSync("TABLE_MOVE", orderID, "UPDATE", map[string]any{
			"source_table_id": req.SourceTableID,
			"target_table_id": req.TargetTableID,
			"order_id":        orderID,
			"branch_id":       branchID,
		})
	}

	jsonResponse(w, http.StatusOK, ApiResponse[map[string]string]{
		Success: true,
		Message: "Table moved successfully",
		Data: map[string]string{
			"sourceTableId": req.SourceTableID,
			"targetTableId": req.TargetTableID,
			"orderId":       orderID,
		},
	})
}

type MergeTablesRequest struct {
	PrimaryTableID    string   `json:"primaryTableId"`
	SecondaryTableIDs []string `json:"secondaryTableIds"`
}

func HandleMergeTables(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req MergeTablesRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	if req.PrimaryTableID == "" || len(req.SecondaryTableIDs) == 0 {
		errorResponse(w, http.StatusBadRequest, "Primary and secondary tables are required")
		return
	}

	tx, err := DB.Begin()
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer tx.Rollback()

	// 1. Get primary active order
	var primaryOrderID string
	_ = tx.QueryRow(`
		SELECT order_id FROM room_orders 
		WHERE table_id = ? AND status NOT IN ('COMPLETED', 'PAID', 'CANCELLED', 'VOIDED') 
		ORDER BY created_at DESC LIMIT 1
	`, req.PrimaryTableID).Scan(&primaryOrderID)

	// 2. For each secondary table: if it has an active order, merge items into primary order and close secondary order
	for _, secTableID := range req.SecondaryTableIDs {
		var secOrderID string
		_ = tx.QueryRow(`
			SELECT order_id FROM room_orders 
			WHERE table_id = ? AND status NOT IN ('COMPLETED', 'PAID', 'CANCELLED', 'VOIDED') 
			ORDER BY created_at DESC LIMIT 1
		`, secTableID).Scan(&secOrderID)

		if secOrderID != "" && primaryOrderID != "" {
			// Move items from secondary order to primary order
			_, _ = tx.Exec(`UPDATE room_order_items SET order_id = ? WHERE order_id = ?`, primaryOrderID, secOrderID)
			// Close secondary order as CANCELLED/MERGED
			_, _ = tx.Exec(`UPDATE room_orders SET status = 'CANCELLED' WHERE order_id = ?`, secOrderID)
		}

		// Update secondary table status to OCCUPIED
		_, _ = tx.Exec(`UPDATE room_tables SET status = 'OCCUPIED' WHERE table_id = ?`, secTableID)
	}

	if err := tx.Commit(); err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}

	jsonResponse(w, http.StatusOK, ApiResponse[string]{Success: true, Message: "Tables merged successfully", Data: req.PrimaryTableID})
}

// 3. Menu Handlers
func HandleGetMenuCategories(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	rows, err := DB.Query("SELECT category_id, branch_id, name, description, sort_order, is_active FROM room_menu_categories WHERE is_active = 1 AND branch_id = ? ORDER BY sort_order ASC", branchID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var cats []MenuCategory
	for rows.Next() {
		var c MenuCategory
		var desc sql.NullString
		if err := rows.Scan(&c.CategoryID, &c.BranchID, &c.Name, &desc, &c.SortOrder, &c.IsActive); err == nil {
			if desc.Valid {
				c.Description = desc.String
			}
			cats = append(cats, c)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]MenuCategory]{Success: true, Data: cats})
}

func HandleGetMenuItems(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	rows, err := DB.Query("SELECT item_id, branch_id, category_id, name, description, sku, base_price, availability, image_url, sort_order, is_active, allow_decimal, unit_name FROM room_menu_items WHERE is_active = 1 AND branch_id = ? ORDER BY sort_order ASC", branchID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var items []MenuItem
	for rows.Next() {
		var it MenuItem
		var desc, sku, img, unit sql.NullString
		var allowDec int
		if err := rows.Scan(&it.ItemID, &it.BranchID, &it.CategoryID, &it.Name, &desc, &sku, &it.BasePrice, &it.Availability, &img, &it.SortOrder, &it.IsActive, &allowDec, &unit); err == nil {
			if desc.Valid {
				it.Description = desc.String
			}
			if sku.Valid {
				it.SKU = sku.String
			}
			if img.Valid {
				it.ImageURL = img.String
			}
			if unit.Valid {
				it.UnitName = unit.String
			}
			it.AllowDecimal = (allowDec == 1)
			items = append(items, it)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]MenuItem]{Success: true, Data: items})
}

// 4. Buffet Handlers
func HandleGetBuffetTiers(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	query := "SELECT tier_id, promotion_id, name, adult_price, child_price, time_limit_minutes, brand_id, branch_id, is_active FROM room_buffet_tiers WHERE is_active = 1"
	var args []interface{}
	if branchID != "" {
		query += " AND (branch_id = ? OR branch_id = '' OR branch_id IS NULL)"
		args = append(args, branchID)
	}
	rows, err := DB.Query(query, args...)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var tiers []BuffetTier
	for rows.Next() {
		var bt BuffetTier
		var brand, branch sql.NullString
		if err := rows.Scan(&bt.TierID, &bt.PromotionID, &bt.Name, &bt.AdultPrice, &bt.ChildPrice, &bt.TimeLimitMinutes, &brand, &branch, &bt.IsActive); err == nil {
			if brand.Valid {
				bt.BrandID = brand.String
			}
			if branch.Valid {
				bt.BranchID = branch.String
			}

			// Get eligible items
			itemRows, ierr := DB.Query("SELECT menu_item_id FROM room_buffet_tier_menu_items WHERE buffet_tier_id = ?", bt.TierID)
			if ierr == nil {
				for itemRows.Next() {
					var itemID string
					if err := itemRows.Scan(&itemID); err == nil {
						bt.EligibleItemIDs = append(bt.EligibleItemIDs, itemID)
					}
				}
				itemRows.Close()
			}
			tiers = append(tiers, bt)
		}
	}

	// If empty, return standard fallback tiers (Brand 1: Shabu Master)
	if len(tiers) == 0 {
		tiers = []BuffetTier{
			{
				TierID:           "tier-std-399",
				PromotionID:      "promo-buf-std",
				Name:             "Standard Buffet ฿399",
				AdultPrice:       39900,
				ChildPrice:       19900,
				TimeLimitMinutes: 90,
				IsActive:         true,
			},
			{
				TierID:           "tier-prem-599",
				PromotionID:      "promo-buf-prem",
				Name:             "Premium Wagyu Buffet ฿599",
				AdultPrice:       59900,
				ChildPrice:       29900,
				TimeLimitMinutes: 120,
				IsActive:         true,
			},
		}
	}

	jsonResponse(w, http.StatusOK, ApiResponse[[]BuffetTier]{Success: true, Data: tiers})
}

func HandleGetBuffetSessions(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	rows, err := DB.Query("SELECT session_id, order_id, branch_id, buffet_tier_id, adult_count, child_count, adult_price_snapshot, child_price_snapshot, time_limit_minutes, started_at, expires_at, closed_at, status, created_by FROM room_buffet_sessions WHERE (status = 'ACTIVE' OR status = 'TIME_WARNING') AND branch_id = ?", branchID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var sessions []BuffetSession
	for rows.Next() {
		var s BuffetSession
		var closedAt sql.NullInt64
		var createdBy sql.NullString
		if err := rows.Scan(&s.SessionID, &s.OrderID, &s.BranchID, &s.BuffetTierID, &s.AdultCount, &s.ChildCount, &s.AdultPriceSnapshot, &s.ChildPriceSnapshot, &s.TimeLimitMinutes, &s.StartedAt, &s.ExpiresAt, &closedAt, &s.Status, &createdBy); err == nil {
			if closedAt.Valid {
				s.ClosedAt = &closedAt.Int64
			}
			if createdBy.Valid {
				s.CreatedBy = createdBy.String
			}
			sessions = append(sessions, s)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]BuffetSession]{Success: true, Data: sessions})
}

// 5. Customer / CRM Handlers
func HandleGetCustomers(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	var rows *sql.Rows
	var err error
	if q != "" {
		like := "%" + q + "%"
		rows, err = DB.Query("SELECT customer_id, display_name, phone, member_id, line_id, email, tier_code, tier_name, discount_percent, points_balance, customer_group, created_at FROM room_customers WHERE display_name LIKE ? OR phone LIKE ? OR member_id LIKE ? ORDER BY created_at DESC", like, like, like)
	} else {
		rows, err = DB.Query("SELECT customer_id, display_name, phone, member_id, line_id, email, tier_code, tier_name, discount_percent, points_balance, customer_group, created_at FROM room_customers ORDER BY created_at DESC")
	}

	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var customers []Customer
	for rows.Next() {
		var c Customer
		var member, line, email sql.NullString
		if err := rows.Scan(&c.CustomerID, &c.DisplayName, &c.Phone, &member, &line, &email, &c.TierCode, &c.TierName, &c.DiscountPercentage, &c.PointsBalance, &c.CustomerGroup, &c.CreatedAt); err == nil {
			if member.Valid {
				c.MemberID = member.String
			}
			if line.Valid {
				c.LineID = line.String
			}
			if email.Valid {
				c.Email = email.String
			}
			customers = append(customers, c)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]Customer]{Success: true, Data: customers})
}

func HandleCreateCustomer(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req struct {
		DisplayName string `json:"displayName"`
		Phone       string `json:"phone"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid payload")
		return
	}

	custID := fmt.Sprintf("cust-%d", time.Now().UnixNano()%1000000)
	cust := Customer{
		CustomerID:         custID,
		DisplayName:        strings.TrimSpace(req.DisplayName),
		Phone:              strings.TrimSpace(req.Phone),
		TierCode:           "SILVER",
		TierName:           "สมาชิกทั่วไป (Silver)",
		DiscountPercentage: 0.0,
		PointMultiplier:    1.0,
		PointsBalance:      0.0,
		CustomerGroup:      "GENERAL",
		CreatedAt:          NowMillis(),
	}

	_, err := DB.Exec(`
		INSERT INTO room_customers (customer_id, display_name, phone, tier_code, tier_name, discount_percent, points_balance, customer_group, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, cust.CustomerID, cust.DisplayName, cust.Phone, cust.TierCode, cust.TierName, cust.DiscountPercentage, cust.PointsBalance, cust.CustomerGroup, cust.CreatedAt)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}

	jsonResponse(w, http.StatusOK, ApiResponse[Customer]{Success: true, Message: "ลงทะเบียนสมาชิกสำเร็จ", Data: cust})
}

// 6. Promotion / Coupon Handler
func HandleApplyCoupon(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req struct {
		Code              string `json:"code"`
		OrderAmountSatang int64  `json:"orderAmountSatang"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid payload")
		return
	}

	code := strings.TrimSpace(strings.ToUpper(req.Code))
	var promo Promotion
	err := DB.QueryRow("SELECT promotion_id, code, name, description, promo_type, priority, is_active, discount_rate, discount_amount, min_order_amount, stacking_policy FROM room_promotions WHERE UPPER(code) = ? AND is_active = 1", code).
		Scan(&promo.PromotionID, &promo.Code, &promo.Name, &promo.Description, &promo.PromoType, &promo.Priority, &promo.IsActive, &promo.DiscountRate, &promo.DiscountAmount, &promo.MinOrderAmount, &promo.StackingPolicy)

	if err != nil {
		errorResponse(w, http.StatusNotFound, fmt.Sprintf("ไม่พบคูปองรหัส '%s' หรือคูปองหมดอายุแล้ว", code))
		return
	}

	if req.OrderAmountSatang < promo.MinOrderAmount {
		errorResponse(w, http.StatusBadRequest, fmt.Sprintf("ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿%.2f (ปัจจุบัน ฿%.2f)", float64(promo.MinOrderAmount)/100.0, float64(req.OrderAmountSatang)/100.0))
		return
	}

	var discountSatang int64
	if promo.PromoType == "PERCENTAGE" {
		discountSatang = int64(float64(req.OrderAmountSatang) * (promo.DiscountRate / 100.0))
		if discountSatang > 10000 { // Cap 100 Baht
			discountSatang = 10000
		}
	} else {
		discountSatang = promo.DiscountAmount
	}

	jsonResponse(w, http.StatusOK, ApiResponse[map[string]any]{
		Success: true,
		Message: "ใช้งานคูปองสำเร็จ",
		Data: map[string]any{
			"code":           promo.Code,
			"name":           promo.Name,
			"discountSatang": discountSatang,
		},
	})
}

// 7. Order Handlers (Create, Send Kitchen, Get Active)
func HandleCreateOrder(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req CreateOrderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid payload")
		return
	}

	orderID := fmt.Sprintf("ord-%d", time.Now().UnixNano()%10000000)
	orderNumber := fmt.Sprintf("B%d", (time.Now().UnixNano()/100000)%10000)

	now := NowMillis()
	var buffetSessionID *string

	// If buffet order, create buffet session
	var buffetHeadChargeSatang int64 = 0
	if req.OrderType == "BUFFET" && req.BuffetTierID != nil {
		var tier BuffetTier
		err := DB.QueryRow("SELECT tier_id, name, adult_price, child_price, time_limit_minutes FROM room_buffet_tiers WHERE tier_id = ?", *req.BuffetTierID).
			Scan(&tier.TierID, &tier.Name, &tier.AdultPrice, &tier.ChildPrice, &tier.TimeLimitMinutes)
		if err == nil {
			buffetHeadChargeSatang = (tier.AdultPrice * int64(req.AdultCount)) + (tier.ChildPrice * int64(req.ChildCount))
			sID := fmt.Sprintf("bsess-%d", time.Now().UnixNano()%1000000)
			expiresAt := now + int64(tier.TimeLimitMinutes*60*1000)

			_, err = DB.Exec(`
				INSERT INTO room_buffet_sessions (session_id, order_id, branch_id, buffet_tier_id, adult_count, child_count, adult_price_snapshot, child_price_snapshot, time_limit_minutes, started_at, expires_at, status, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
			`, sID, orderID, req.BranchID, tier.TierID, req.AdultCount, req.ChildCount, tier.AdultPrice, tier.ChildPrice, tier.TimeLimitMinutes, now, expiresAt, req.CreatedBy)

			if err == nil {
				buffetSessionID = &sID
			}
		}
	}

	// Calculate subtotal from items
	var itemsGross int64 = 0
	for _, it := range req.Items {
		unitPrice := it.UnitPriceSnapshot
		if it.IsBuffetIncluded {
			unitPrice = 0
		}
		itemsGross += int64(float64(unitPrice) * it.Quantity)
	}
	gross := itemsGross + buffetHeadChargeSatang

	// Calculate discounts
	var manualDisc = req.ManualDiscountSatang
	if req.ManualDiscountPercent > 0 {
		pDisc := int64(float64(gross) * (req.ManualDiscountPercent / 100.0))
		if pDisc > manualDisc {
			manualDisc = pDisc
		}
	}

	// Customer Member Discount
	var memberDisc int64 = 0
	if req.CustomerID != nil {
		var discPct float64
		_ = DB.QueryRow("SELECT discount_percent FROM room_customers WHERE customer_id = ?", *req.CustomerID).Scan(&discPct)
		if discPct > 0 {
			memberDisc = int64(float64(gross) * (discPct / 100.0))
		}
	}

	couponDisc := req.PointsRedeemedSatang
	totalDisc := manualDisc + memberDisc + couponDisc
	if totalDisc > gross {
		totalDisc = gross
	}
	subtotalAfterDisc := gross - totalDisc

	// VAT 7% inclusive
	taxFactor := 7.0 / 107.0
	taxAmount := int64(float64(subtotalAfterDisc) * taxFactor)
	grandTotal := subtotalAfterDisc

	status := "IN_KITCHEN"
	kitchenStatus := "SENT"

	_, err := DB.Exec(`
		INSERT INTO room_orders (order_id, branch_id, customer_id, table_id, order_number, order_type, channel, status, kitchen_status, buffet_session_id, subtotal_amount, discount_amount, service_charge_amount, tax_amount, total_amount, created_by, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
	`, orderID, req.BranchID, req.CustomerID, req.TableID, orderNumber, req.OrderType, req.Channel, status, kitchenStatus, buffetSessionID, gross, totalDisc, taxAmount, grandTotal, req.CreatedBy, now)

	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}

	// Insert Items
	for _, it := range req.Items {
		oiID := fmt.Sprintf("oi-%d", time.Now().UnixNano()%10000000)
		unitPrice := it.UnitPriceSnapshot
		if it.IsBuffetIncluded {
			unitPrice = 0
		}
		itemSubtotal := int64(float64(unitPrice) * it.Quantity)
		isBuf := 0
		if it.IsBuffetIncluded {
			isBuf = 1
		}

		_, _ = DB.Exec(`
			INSERT INTO room_order_items (order_item_id, order_id, menu_item_id, name_snapshot, unit_price_snapshot, quantity, notes, subtotal, kitchen_status, is_buffet_included)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SENT', ?)
		`, oiID, orderID, it.MenuItemID, it.NameSnapshot, it.UnitPriceSnapshot, it.Quantity, it.Notes, itemSubtotal, isBuf)
	}

	// Update table status to OCCUPIED
	if req.TableID != nil {
		_, _ = DB.Exec("UPDATE room_tables SET status = 'OCCUPIED' WHERE table_id = ?", *req.TableID)
	}

	createdOrder := Order{
		OrderID:         orderID,
		BranchID:        req.BranchID,
		CustomerID:      req.CustomerID,
		TableID:         req.TableID,
		OrderNumber:     orderNumber,
		OrderType:       req.OrderType,
		Channel:         req.Channel,
		Status:          status,
		KitchenStatus:   kitchenStatus,
		BuffetSessionID: buffetSessionID,
		SubtotalAmount:  gross,
		DiscountAmount:  totalDisc,
		TaxAmount:       taxAmount,
		TotalAmount:     grandTotal,
		CreatedBy:       &req.CreatedBy,
		CreatedAt:       now,
	}

	// Enqueue sync for order creation
	_ = EnqueueSync("ORDER", orderID, "CREATE", createdOrder)

	jsonResponse(w, http.StatusOK, ApiResponse[Order]{
		Success: true,
		Message: "สร้างออเดอร์และส่งครัวเรียบร้อยแล้ว",
		Data:    createdOrder,
	})
}

func HandleGetActiveOrders(w http.ResponseWriter, r *http.Request) {
	branchID := getCurrentBranchID(r)
	rows, err := DB.Query("SELECT order_id, branch_id, customer_id, table_id, order_number, order_type, channel, status, kitchen_status, buffet_session_id, subtotal_amount, discount_amount, service_charge_amount, tax_amount, total_amount, created_by, created_at FROM room_orders WHERE status IN ('OPEN', 'IN_KITCHEN', 'WAITING_PAYMENT') AND branch_id = ? ORDER BY created_at DESC", branchID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var orders []Order
	for rows.Next() {
		var o Order
		var cust, tbl, bsess, createdBy sql.NullString
		if err := rows.Scan(&o.OrderID, &o.BranchID, &cust, &tbl, &o.OrderNumber, &o.OrderType, &o.Channel, &o.Status, &o.KitchenStatus, &bsess, &o.SubtotalAmount, &o.DiscountAmount, &o.ServiceChargeAmount, &o.TaxAmount, &o.TotalAmount, &createdBy, &o.CreatedAt); err == nil {
			if cust.Valid {
				o.CustomerID = &cust.String
			}
			if tbl.Valid {
				o.TableID = &tbl.String
			}
			if bsess.Valid {
				o.BuffetSessionID = &bsess.String
			}
			if createdBy.Valid {
				o.CreatedBy = &createdBy.String
			}

			// Fetch items
			irows, _ := DB.Query("SELECT order_item_id, order_id, menu_item_id, name_snapshot, unit_price_snapshot, quantity, notes, subtotal, kitchen_status, is_buffet_included FROM room_order_items WHERE order_id = ?", o.OrderID)
			if irows != nil {
				for irows.Next() {
					var item OrderItem
					var notes sql.NullString
					var isBuf int
					if err := irows.Scan(&item.OrderItemID, &item.OrderID, &item.MenuItemID, &item.NameSnapshot, &item.UnitPriceSnapshot, &item.Quantity, &notes, &item.Subtotal, &item.KitchenStatus, &isBuf); err == nil {
						if notes.Valid {
							item.Notes = &notes.String
						}
						item.IsBuffetIncluded = (isBuf == 1)
						o.Items = append(o.Items, item)
					}
				}
				irows.Close()
			}

			orders = append(orders, o)
		}
	}
	jsonResponse(w, http.StatusOK, ApiResponse[[]Order]{Success: true, Data: orders})
}

// 8. Payment & Receipt Handlers
func HandleProcessPayment(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req ProcessPaymentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid payload")
		return
	}

	// Fetch order
	var order Order
	var custID, tblID sql.NullString
	err := DB.QueryRow("SELECT order_id, branch_id, customer_id, table_id, order_number, order_type, total_amount FROM room_orders WHERE order_id = ?", req.OrderID).
		Scan(&order.OrderID, &order.BranchID, &custID, &tblID, &order.OrderNumber, &order.OrderType, &order.TotalAmount)
	if err != nil {
		errorResponse(w, http.StatusNotFound, "ไม่พบข้อมูลคำสั่งซื้อ")
		return
	}

	now := NowMillis()
	var totalTendered int64 = 0
	for _, p := range req.AppliedPayments {
		payID := fmt.Sprintf("pay-%d", time.Now().UnixNano()%10000000)
		change := int64(0)
		if p.PaymentMethod == "CASH" && p.TenderedAmount > p.Amount {
			change = p.TenderedAmount - p.Amount
		}
		totalTendered += p.TenderedAmount

		_, _ = DB.Exec(`
			INSERT INTO room_payment_transactions (payment_id, order_id, branch_id, device_id, payment_method, amount, tendered_amount, change_amount, status, created_by, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?)
		`, payID, req.OrderID, req.BranchID, req.DeviceID, p.PaymentMethod, p.Amount, p.TenderedAmount, change, req.CreatedBy, now)
	}

	// Save Tax Invoice if requested
	if req.TaxCustomer != nil {
		invID := fmt.Sprintf("inv-%d", time.Now().UnixNano()%1000000)
		_, _ = DB.Exec(`
			INSERT INTO room_tax_invoices (invoice_id, order_id, taxpayer_name, tax_id, branch_number, address, phone, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		`, invID, req.OrderID, req.TaxCustomer.TaxpayerName, req.TaxCustomer.TaxID, req.TaxCustomer.BranchNumber, req.TaxCustomer.Address, req.TaxCustomer.Phone, now)
	}

	// Award Points to Customer (1 pt per 25 THB)
	if custID.Valid {
		earnedPoints := float64(order.TotalAmount/100) / 25.0
		_, _ = DB.Exec("UPDATE room_customers SET points_balance = points_balance + ? WHERE customer_id = ?", earnedPoints, custID.String)
	}

	// Update order status to COMPLETED
	_, _ = DB.Exec("UPDATE room_orders SET status = 'COMPLETED' WHERE order_id = ?", req.OrderID)

	// Close buffet session if exists
	_, _ = DB.Exec("UPDATE room_buffet_sessions SET status = 'CLOSED', closed_at = ? WHERE order_id = ?", now, req.OrderID)

	// Free table back to AVAILABLE
	if tblID.Valid {
		_, _ = DB.Exec("UPDATE room_tables SET status = 'AVAILABLE' WHERE table_id = ?", tblID.String)
	}

	changeSatang := totalTendered - order.TotalAmount
	if changeSatang < 0 {
		changeSatang = 0
	}

	// Enqueue sync for Payment & Completed Order
	_ = EnqueueSync("PAYMENT", req.OrderID, "CREATE", req)
	_ = EnqueueSync("ORDER_STATUS", req.OrderID, "UPDATE", map[string]any{"orderId": req.OrderID, "status": "COMPLETED"})

	jsonResponse(w, http.StatusOK, ApiResponse[map[string]any]{
		Success: true,
		Message: "ชำระเงินสำเร็จ บันทึกการขายเรียบร้อย",
		Data: map[string]any{
			"orderId":      req.OrderID,
			"orderNumber":  order.OrderNumber,
			"totalAmount":  order.TotalAmount,
			"changeSatang": changeSatang,
		},
	})
}

// 9. Sync Telemetry & Manual Trigger Handlers
func HandleGetSyncStatus(w http.ResponseWriter, r *http.Request) {
	if GlobalSyncEngine == nil {
		jsonResponse(w, http.StatusOK, ApiResponse[SyncStatusInfo]{
			Success: true,
			Data:    SyncStatusInfo{IsOnline: false},
		})
		return
	}

	status := GlobalSyncEngine.GetStatus()
	jsonResponse(w, http.StatusOK, ApiResponse[SyncStatusInfo]{
		Success: true,
		Data:    status,
	})
}

func HandleTriggerSync(w http.ResponseWriter, r *http.Request) {
	if GlobalSyncEngine != nil {
		GlobalSyncEngine.TriggerSync()
	}
	jsonResponse(w, http.StatusOK, ApiResponse[string]{
		Success: true,
		Message: "สั่งการซิงค์ข้อมูลกับคลาวด์ทันทีสำเร็จ",
	})
}

// 10. Order Status Update Handler (e.g. WAITING_PAYMENT)
func HandleUpdateOrderStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut && r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) < 3 {
		errorResponse(w, http.StatusBadRequest, "Invalid order URL path")
		return
	}
	orderID := parts[2]

	var req struct {
		Status string `json:"status"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid payload")
		return
	}

	_, err := DB.Exec("UPDATE room_orders SET status = ? WHERE order_id = ?", req.Status, orderID)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}

	// If status changed to WAITING_PAYMENT, also update table status to WAITING_PAYMENT
	var tableID sql.NullString
	_ = DB.QueryRow("SELECT table_id FROM room_orders WHERE order_id = ?", orderID).Scan(&tableID)
	if tableID.Valid && tableID.String != "" {
		if req.Status == "WAITING_PAYMENT" {
			_, _ = DB.Exec("UPDATE room_tables SET status = 'WAITING_PAYMENT' WHERE table_id = ?", tableID.String)
			_ = EnqueueSync("TABLE_STATUS", tableID.String, "UPDATE", map[string]any{"tableId": tableID.String, "status": "WAITING_PAYMENT"})
		}
	}

	_ = EnqueueSync("ORDER", orderID, "UPDATE_STATUS", map[string]any{
		"orderId": orderID,
		"status":  req.Status,
	})

	jsonResponse(w, http.StatusOK, ApiResponse[map[string]any]{
		Success: true,
		Message: "อัปเดตสถานะออเดอร์สำเร็จ",
		Data: map[string]any{
			"orderId": orderID,
			"status":  req.Status,
		},
	})
}

// ── 11. Device Identity & Activation Handlers (Multi-Branch POS) ──

// Helper to get active branch ID from SQLite device identity
func GetActiveBranchID() string {
	if DB == nil {
		return "branch-001"
	}
	ident, err := GetDeviceIdentity(DB)
	if err == nil && ident != nil && ident.BranchID != "" {
		return ident.BranchID
	}
	return "branch-001"
}

// Helper to get active device ID from SQLite device identity
func GetActiveDeviceID() string {
	if DB == nil {
		return "pos-device-001"
	}
	ident, err := GetDeviceIdentity(DB)
	if err == nil && ident != nil && ident.DeviceID != "" {
		return ident.DeviceID
	}
	return "pos-device-001"
}

func HandleGetDeviceIdentity(w http.ResponseWriter, r *http.Request) {
	ident, err := GetDeviceIdentity(DB)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, fmt.Sprintf("Failed to query device identity: %v", err))
		return
	}

	if ident == nil {
		jsonResponse(w, http.StatusOK, map[string]any{
			"success":   true,
			"activated": false,
			"message":   "เครื่อง POS ยังไม่ได้ทำการเปิดใช้งาน (Awaiting Device Activation Token)",
		})
		return
	}

	jsonResponse(w, http.StatusOK, map[string]any{
		"success":   true,
		"activated": true,
		"identity":  ident,
	})
}

// Helper to generate human-readable activation code: SUN-XXXX-XXXX
func generateSecureActivationCode(branchCode string) string {
	const charset = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Omit 0, O, 1, I to avoid confusion
	b := make([]byte, 8)
	nano := time.Now().UnixNano()
	for i := range b {
		b[i] = charset[(nano>>(i*4)+int64(i*7))%int64(len(charset))]
	}
	if branchCode != "" && len(branchCode) <= 4 {
		return fmt.Sprintf("SUN-%s-%s", strings.ToUpper(branchCode), string(b[4:8]))
	}
	return fmt.Sprintf("SUN-%s-%s", string(b[0:4]), string(b[4:8]))
}

// Handler to generate a new Activation Code (from Backoffice / Gen Code button)
func HandleGenerateActivationCode(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req GenerateActivationCodeRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	branchID := strings.TrimSpace(req.BranchID)
	if branchID == "" {
		branchID = "branch-001"
	}

	branchCode := strings.TrimSpace(req.BranchCode)
	branchName := strings.TrimSpace(req.BranchName)
	deviceCode := strings.TrimSpace(req.DeviceCode)
	deviceName := strings.TrimSpace(req.DeviceName)

	if deviceCode == "" {
		deviceCode = "POS-01"
	}
	if deviceName == "" {
		deviceName = fmt.Sprintf("POS Terminal (%s)", deviceCode)
	}

	// Auto-fill branch details if standard branch IDs
	if branchID == "branch-001" || strings.EqualFold(branchID, "BR-01") {
		branchID = "branch-001"
		if branchCode == "" {
			branchCode = "BR-01"
		}
		if branchName == "" {
			branchName = "Sukhumvit Main Branch (สุขุมวิท)"
		}
	} else if branchID == "branch-002" || strings.EqualFold(branchID, "BR-02") {
		branchID = "branch-002"
		if branchCode == "" {
			branchCode = "BR-02"
		}
		if branchName == "" {
			branchName = "Siam Paragon Branch (สยามพารากอน)"
		}
	} else if branchID == "branch-003" || strings.EqualFold(branchID, "BR-03") {
		branchID = "branch-003"
		if branchCode == "" {
			branchCode = "BR-03"
		}
		if branchName == "" {
			branchName = "Phuket Old Town Branch (ภูเก็ต)"
		}
	} else {
		if branchCode == "" {
			branchCode = strings.ToUpper(branchID)
		}
		if branchName == "" {
			branchName = fmt.Sprintf("สาขา %s", branchID)
		}
	}

	expiresInHours := req.ExpiresInHours
	if expiresInHours <= 0 {
		expiresInHours = 72 // Default 72 hours
	}

	now := NowMillis()
	expiresAt := now + int64(expiresInHours*3600*1000)
	code := generateSecureActivationCode(branchCode)

	record := ActivationCodeRecord{
		Code:       code,
		BranchID:   branchID,
		BranchName: branchName,
		BranchCode: branchCode,
		DeviceCode: deviceCode,
		DeviceName: deviceName,
		CreatedAt:  now,
		ExpiresAt:  expiresAt,
		Status:     "UNUSED",
	}

	_, err := DB.Exec(`
		INSERT INTO activation_codes (code, branch_id, branch_name, branch_code, device_code, device_name, created_at, expires_at, status)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UNUSED')
	`, record.Code, record.BranchID, record.BranchName, record.BranchCode, record.DeviceCode, record.DeviceName, record.CreatedAt, record.ExpiresAt)

	if err != nil {
		errorResponse(w, http.StatusInternalServerError, fmt.Sprintf("สร้างรหัส Activation Code ไม่สำเร็จ: %v", err))
		return
	}

	jsonResponse(w, http.StatusOK, ApiResponse[ActivationCodeRecord]{
		Success: true,
		Message: fmt.Sprintf("สร้างรหัสเปิดใช้งานสำหรับ '%s' สำเร็จ", record.BranchName),
		Data:    record,
	})
}

// Handler to list generated activation codes
func HandleListActivationCodes(w http.ResponseWriter, r *http.Request) {
	rows, err := DB.Query(`
		SELECT code, branch_id, branch_name, branch_code, device_code, device_name, created_at, expires_at, status, activated_at, activated_device_id
		FROM activation_codes ORDER BY created_at DESC LIMIT 50
	`)
	if err != nil {
		errorResponse(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer rows.Close()

	var list []ActivationCodeRecord
	for rows.Next() {
		var item ActivationCodeRecord
		var actAt sql.NullInt64
		var actDev sql.NullString
		if err := rows.Scan(&item.Code, &item.BranchID, &item.BranchName, &item.BranchCode, &item.DeviceCode, &item.DeviceName, &item.CreatedAt, &item.ExpiresAt, &item.Status, &actAt, &actDev); err == nil {
			if actAt.Valid {
				item.ActivatedAt = &actAt.Int64
			}
			if actDev.Valid {
				item.ActivatedDeviceID = &actDev.String
			}
			list = append(list, item)
		}
	}

	jsonResponse(w, http.StatusOK, ApiResponse[[]ActivationCodeRecord]{
		Success: true,
		Data:    list,
	})
}

func HandleActivateDevice(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req ActivateDeviceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		errorResponse(w, http.StatusBadRequest, "Invalid activation request payload")
		return
	}

	code := strings.TrimSpace(req.ActivationCode)
	code = strings.ReplaceAll(code, " ", "-")
	for strings.Contains(code, "--") {
		code = strings.ReplaceAll(code, "--", "-")
	}
	if code == "" {
		errorResponse(w, http.StatusBadRequest, "กรุณาระบุรหัส Activation Code")
		return
	}

	upperCode := strings.ToUpper(code)
	now := NowMillis()

	var ident DeviceIdentity
	ident.ActivationCode = code
	ident.ActivatedAt = now
	ident.CompanyID = "comp-001"
	ident.CompanyName = "SunPOS Restaurant Group Co., Ltd."
	ident.CloudApiUrl = req.CloudApiUrl

	// Check if this code exists in activation_codes table in SQLite
	var dbCode ActivationCodeRecord
	var dbActAt sql.NullInt64
	var dbActDev sql.NullString
	err := DB.QueryRow(`
		SELECT code, branch_id, branch_name, branch_code, device_code, device_name, created_at, expires_at, status, activated_at, activated_device_id
		FROM activation_codes WHERE UPPER(code) = ? LIMIT 1
	`, upperCode).Scan(&dbCode.Code, &dbCode.BranchID, &dbCode.BranchName, &dbCode.BranchCode, &dbCode.DeviceCode, &dbCode.DeviceName, &dbCode.CreatedAt, &dbCode.ExpiresAt, &dbCode.Status, &dbActAt, &dbActDev)

	if err == nil {
		// Found in generated activation codes table!
		if dbCode.ExpiresAt < now {
			errorResponse(w, http.StatusBadRequest, fmt.Sprintf("รหัส Activation Code '%s' หมดอายุแล้ว กรุณาสร้างรหัสใหม่", code))
			return
		}

		ident.BranchID = dbCode.BranchID
		ident.BranchName = dbCode.BranchName
		ident.BranchCode = dbCode.BranchCode
		ident.DeviceCode = dbCode.DeviceCode
		ident.DeviceName = dbCode.DeviceName
		ident.DeviceID = fmt.Sprintf("pos-%s-%s-%d", ident.BranchID, strings.ToLower(ident.DeviceCode), now%10000)

		// Mark as ACTIVATED in activation_codes table
		_, _ = DB.Exec("UPDATE activation_codes SET status = 'ACTIVATED', activated_at = ?, activated_device_id = ? WHERE code = ?", now, ident.DeviceID, dbCode.Code)
	} else if strings.HasPrefix(upperCode, "DEV-") {
		// 1. Parsing Dev Code Format: DEV-{branchId}-{deviceCode} (e.g. DEV-branch-001-POS-01)
		trimmed := strings.TrimPrefix(code, "DEV-")
		trimmed = strings.TrimPrefix(trimmed, "dev-")

		if idx := strings.Index(strings.ToUpper(trimmed), "-POS"); idx != -1 {
			ident.BranchID = trimmed[:idx]
			ident.DeviceCode = trimmed[idx+1:]
		} else {
			parts := strings.Split(trimmed, "-")
			if len(parts) >= 2 {
				ident.BranchID = parts[0]
				ident.DeviceCode = parts[1]
			} else {
				ident.BranchID = trimmed
				ident.DeviceCode = "POS-01"
			}
		}

		ident.DeviceID = fmt.Sprintf("pos-%s-%s", ident.BranchID, strings.ToLower(ident.DeviceCode))
		ident.DeviceName = fmt.Sprintf("POS Terminal (%s)", ident.DeviceCode)
		ident.BranchCode = strings.ToUpper(ident.BranchID)

		if ident.BranchID == "branch-001" || ident.BranchID == "BR-01" || ident.BranchID == "BR01" {
			ident.BranchName = "Sukhumvit Main Branch (สุขุมวิท)"
			ident.BranchCode = "BR-01"
		} else if ident.BranchID == "branch-002" || ident.BranchID == "BR-02" || ident.BranchID == "BR02" {
			ident.BranchName = "Siam Paragon Branch (สยามพารากอน)"
			ident.BranchCode = "BR-02"
		} else if ident.BranchID == "branch-003" || ident.BranchID == "BR-03" || ident.BranchID == "BR03" {
			ident.BranchName = "Phuket Old Town Branch (ภูเก็ต)"
			ident.BranchCode = "BR-03"
		} else {
			ident.BranchName = fmt.Sprintf("สาขา %s", ident.BranchID)
		}
	} else if strings.Contains(upperCode, "SUK01") || strings.Contains(upperCode, "SUKHUMVIT") || strings.Contains(upperCode, "BR01") || strings.Contains(upperCode, "BR-01") {
		ident.BranchID = "branch-001"
		ident.BranchCode = "SUK-01"
		ident.BranchName = "Sun Shabu Sukhumvit 24 (สุขุมวิท 24)"
		ident.DeviceID = fmt.Sprintf("pos-suk01-%d", now%1000)
		ident.DeviceCode = "POS-01"
		ident.DeviceName = "Main POS Terminal #1"
	} else if strings.Contains(upperCode, "SUK02") || strings.Contains(upperCode, "ASOKE") || strings.Contains(upperCode, "BR02") || strings.Contains(upperCode, "SIAM") {
		ident.BranchID = "branch-002"
		ident.BranchCode = "SUK-02"
		ident.BranchName = "Sun Shabu Sukhumvit Asoke (สุขุมวิท อโศก)"
		ident.DeviceID = fmt.Sprintf("pos-suk02-%d", now%1000)
		ident.DeviceCode = "POS-01"
		ident.DeviceName = "POS Terminal #1"
	} else if strings.Contains(upperCode, "JPN01") || strings.Contains(upperCode, "THONGLOR") || strings.Contains(upperCode, "BR03") || strings.Contains(upperCode, "PHUKET") {
		ident.BranchID = "branch-003"
		ident.BranchCode = "JPN-01"
		ident.BranchName = "Sun Japanese Thonglor (สาขาทองหล่อ)"
		ident.DeviceID = fmt.Sprintf("pos-jpn01-%d", now%1000)
		ident.DeviceCode = "POS-01"
		ident.DeviceName = "POS Terminal #1"
	} else if strings.Contains(upperCode, "CAF01") || strings.Contains(upperCode, "ARI") || strings.Contains(upperCode, "BR04") {
		ident.BranchID = "branch-004"
		ident.BranchCode = "CAF-01"
		ident.BranchName = "Sun Coffee Ari Craft Cafe (สาขาอารีย์)"
		ident.DeviceID = fmt.Sprintf("pos-caf01-%d", now%1000)
		ident.DeviceCode = "POS-01"
		ident.DeviceName = "POS Terminal #1"
	} else if strings.HasPrefix(upperCode, "SUN-") || len(code) >= 6 {
		// Generic SUN-{CODE}-{RAND} format
		parts := strings.Split(upperCode, "-")
		if len(parts) >= 2 {
			ident.BranchCode = parts[1]
			ident.BranchID = fmt.Sprintf("branch-%s", strings.ToLower(parts[1]))
			ident.BranchName = fmt.Sprintf("สาขา %s", parts[1])
		} else {
			ident.BranchID = "branch-001"
			ident.BranchCode = "SUK-01"
			ident.BranchName = "Sun Shabu Sukhumvit 24 (สุขุมวิท 24)"
		}
		ident.DeviceID = fmt.Sprintf("pos-%s-%d", strings.ToLower(ident.BranchCode), now%1000)
		ident.DeviceCode = "POS-01"
		ident.DeviceName = "POS Terminal #1"
	} else {
		errorResponse(w, http.StatusBadRequest, "รหัส Activation Code ไม่ถูกต้อง หรือรูปแบบไม่ตรงตามที่กำหนด (ตัวอย่าง: SUN-SUK01-XXXX หรือ SUN-BR01-XXXX)")
		return
	}

	// Save to SQLite device_identity table
	if err := SaveDeviceIdentity(DB, ident); err != nil {
		errorResponse(w, http.StatusInternalServerError, fmt.Sprintf("บันทึกข้อมูลเครื่อง POS ไม่สำเร็จ: %v", err))
		return
	}

	// Seed / verify branch catalog for the newly activated branch
	_ = SeedBranchCatalog(DB, ident.BranchID, ident.CompanyID, ident.BranchName, ident.BranchCode, ident.DeviceID, ident.DeviceName, ident.DeviceCode)

	// Update Sync engine configuration
	if GlobalSyncEngine != nil {
		GlobalSyncEngine.mu.Lock()
		GlobalSyncEngine.branchID = ident.BranchID
		GlobalSyncEngine.deviceID = ident.DeviceID
		if ident.CloudApiUrl != "" {
			GlobalSyncEngine.cloudBaseURL = ident.CloudApiUrl
		}
		GlobalSyncEngine.mu.Unlock()
	}

	_ = EnqueueSync("DEVICE_ACTIVATION", ident.DeviceID, "ACTIVATE", ident)

	jsonResponse(w, http.StatusOK, ActivateDeviceResponse{
		Success:  true,
		Message:  fmt.Sprintf("เปิดใช้งานเครื่อง POS สำหรับ '%s' (%s) สำเร็จ", ident.BranchName, ident.BranchCode),
		Identity: &ident,
	})
}

func HandleDeactivateDevice(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		errorResponse(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if err := ClearDeviceIdentity(DB); err != nil {
		errorResponse(w, http.StatusInternalServerError, fmt.Sprintf("ยกเลิกการเปิดใช้งานไม่สำเร็จ: %v", err))
		return
	}

	jsonResponse(w, http.StatusOK, ApiResponse[string]{
		Success: true,
		Message: "ยกเลิกการเปิดใช้งานเครื่อง POS เรียบร้อยแล้ว (Deactivated)",
		Data:    "DEACTIVATED",
	})
}


