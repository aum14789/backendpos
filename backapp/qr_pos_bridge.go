package main

import (
	"database/sql"
	"fmt"
	"log"
	"strings"
	"time"
)

// ConvertQROrderToRoomOrderTx transforms a cloud QR order into a main POS room_order inside an ACID transaction
func ConvertQROrderToRoomOrderTx(tx *sql.Tx, order *IncomingCloudOrder) (string, error) {
	if tx == nil || order == nil {
		return "", fmt.Errorf("tx or order is nil")
	}

	// 1. Idempotency Check: Don't recreate if this cloud_order_id already has a room_order
	var existingOrderID string
	err := tx.QueryRow("SELECT order_id FROM room_orders WHERE cloud_order_id = ? LIMIT 1", order.OrderID).Scan(&existingOrderID)
	if err == nil && existingOrderID != "" {
		log.Printf("[QROrder-POS] ⏭️ Cloud Order [%s] already converted to room_order [%s]. Skipping duplicate.", order.OrderID, existingOrderID)
		return existingOrderID, nil
	}

	log.Printf("[QROrder-POS] 🔄 Converting QR Order [%s] to POS room_order for Table [%s]...", order.OrderID, order.TableNumber)

	// 2. Resolve Table: Match by name_number or table_id in the branch
	var tableID string
	err = tx.QueryRow(`
		SELECT table_id FROM room_tables 
		WHERE (name_number = ? OR table_id = ?) AND branch_id = ? 
		LIMIT 1
	`, order.TableNumber, order.TableNumber, order.BranchID).Scan(&tableID)

	if err != nil || tableID == "" {
		// Try matching by name_number or table_id without branch filter as fallback
		_ = tx.QueryRow(`
			SELECT table_id FROM room_tables 
			WHERE name_number = ? OR table_id = ? 
			LIMIT 1
		`, order.TableNumber, order.TableNumber).Scan(&tableID)
	}

	if tableID == "" {
		// Auto-provision table record if not present
		tableID = fmt.Sprintf("tbl-%s", strings.ToLower(order.TableNumber))
		_, _ = tx.Exec(`
			INSERT OR IGNORE INTO room_tables (table_id, branch_id, name_number, capacity, status, is_active)
			VALUES (?, ?, ?, 4, 'OCCUPIED', 1)
		`, tableID, order.BranchID, order.TableNumber)
		log.Printf("[QROrder-POS] ℹ️ Auto-provisioned table [%s] (ID: %s) for branch [%s]", order.TableNumber, tableID, order.BranchID)
	}

	// 3. Resolve Table Session: Check if an active session exists for this table
	var tableSessionID sql.NullString
	_ = tx.QueryRow(`
		SELECT session_id FROM room_table_sessions 
		WHERE table_id = ? AND status = 'ACTIVE' 
		ORDER BY opened_at DESC LIMIT 1
	`, tableID).Scan(&tableSessionID)

	var sessionIDStr *string
	nowEpoch := time.Now().UnixMilli()
	if tableSessionID.Valid && tableSessionID.String != "" {
		sessionIDStr = &tableSessionID.String
		log.Printf("[QROrder-POS] 🔗 Linked to existing Table Session [%s] on Table [%s]", *sessionIDStr, order.TableNumber)
	} else {
		// Create a new active table session
		newSessID := fmt.Sprintf("tsess-%d", time.Now().UnixNano()%10000000)
		_, _ = tx.Exec(`
			INSERT INTO room_table_sessions (session_id, table_id, branch_id, opened_at, status, opened_by)
			VALUES (?, ?, ?, ?, 'ACTIVE', 'QR_ORDER')
		`, newSessID, tableID, order.BranchID, nowEpoch)
		sessionIDStr = &newSessID
		log.Printf("[QROrder-POS] 🆕 Created new Table Session [%s] for Table [%s]", newSessID, order.TableNumber)
	}

	// 4. Calculate Satang / Cents Amounts
	// In POS room_orders, amounts are represented as Satang (1 THB = 100 Satang)
	var itemsGrossSatang int64 = 0
	type orderItemPrep struct {
		menuItemID        string
		nameSnapshot      string
		unitPriceSnapshot int64
		quantity          float64
		notes             string
		subtotalSatang    int64
	}
	var preparedItems []orderItemPrep

	for _, item := range order.Items {
		unitPriceSatang := int64(item.UnitPrice * 100)
		qty := float64(item.Quantity)
		if qty <= 0 {
			qty = 1
		}
		itemSubtotalSatang := int64(float64(unitPriceSatang) * qty)
		itemsGrossSatang += itemSubtotalSatang

		// Combine options and note for the kitchen/cashier view
		noteCombined := item.Note
		if item.Options != nil {
			optStr := fmt.Sprintf("%v", item.Options)
			if optStr != "" && optStr != "map[]" {
				if noteCombined != "" {
					noteCombined = fmt.Sprintf("%s (%s)", noteCombined, optStr)
				} else {
					noteCombined = optStr
				}
			}
		}

		preparedItems = append(preparedItems, orderItemPrep{
			menuItemID:        item.ProductID,
			nameSnapshot:      item.ProductName,
			unitPriceSnapshot: unitPriceSatang,
			quantity:          qty,
			notes:             noteCombined,
			subtotalSatang:    itemSubtotalSatang,
		})
	}

	// If order.TotalAmount is provided, ensure totalSatang matches
	totalSatang := itemsGrossSatang
	if order.TotalAmount > 0 && totalSatang == 0 {
		totalSatang = int64(order.TotalAmount * 100)
	}

	// VAT 7% inclusive calculation (consistent with handlers.go line 752)
	taxFactor := 7.0 / 107.0
	taxAmountSatang := int64(float64(totalSatang) * taxFactor)

	// 5. Generate Order Identifiers
	posOrderID := fmt.Sprintf("ord-qr-%d", time.Now().UnixNano()%10000000)
	shortID := order.OrderID
	if len(shortID) > 8 {
		shortID = shortID[:8]
	}
	orderNumber := fmt.Sprintf("QR-%s", shortID)
	createdBy := "QR_ORDER"

	// 6. Insert into room_orders (with Channel = 'QR')
	orderInsertQuery := `
		INSERT INTO room_orders (
			order_id, branch_id, table_id, table_session_id, order_number,
			order_type, channel, status, kitchen_status,
			subtotal_amount, discount_amount, service_charge_amount, tax_amount, total_amount,
			cloud_order_id, created_by, created_at
		) VALUES (?, ?, ?, ?, ?, 'DINE_IN', 'QR', 'IN_KITCHEN', 'SENT', ?, 0, 0, ?, ?, ?, ?, ?)
	`
	_, err = tx.Exec(
		orderInsertQuery,
		posOrderID, order.BranchID, tableID, sessionIDStr, orderNumber,
		itemsGrossSatang, taxAmountSatang, totalSatang,
		order.OrderID, createdBy, nowEpoch,
	)
	if err != nil {
		return "", fmt.Errorf("insert room_orders failed: %w", err)
	}

	// 7. Insert into room_order_items
	itemInsertQuery := `
		INSERT INTO room_order_items (
			order_item_id, order_id, menu_item_id, name_snapshot,
			unit_price_snapshot, quantity, notes, subtotal, kitchen_status, is_buffet_included
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SENT', 0)
	`
	for i, it := range preparedItems {
		oiID := fmt.Sprintf("oi-qr-%d-%d", time.Now().UnixNano()%1000000, i)
		_, err = tx.Exec(
			itemInsertQuery,
			oiID, posOrderID, it.menuItemID, it.nameSnapshot,
			it.unitPriceSnapshot, it.quantity, it.notes, it.subtotalSatang,
		)
		if err != nil {
			return "", fmt.Errorf("insert room_order_items failed: %w", err)
		}
	}

	// 8. Update Table Status to OCCUPIED
	_, _ = tx.Exec("UPDATE room_tables SET status = 'OCCUPIED' WHERE table_id = ?", tableID)

	log.Printf("✅ [QROrder-POS] Successfully created room_order [%s] (Number: %s, Channel: QR, Table: %s, Total: ฿%.2f, Items: %d)",
		posOrderID, orderNumber, order.TableNumber, float64(totalSatang)/100.0, len(preparedItems))

	return posOrderID, nil
}
