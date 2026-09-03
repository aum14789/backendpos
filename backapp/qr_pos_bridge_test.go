package main

import (
	"database/sql"
	"testing"

	_ "modernc.org/sqlite"
)

func TestConvertQROrderToRoomOrder(t *testing.T) {
	// 1. Setup shared in-memory SQLite database
	db, err := sql.Open("sqlite", "file:mem_qr_bridge_test?mode=memory&cache=shared")
	if err != nil {
		t.Fatalf("failed to open memory db: %v", err)
	}
	defer db.Close()

	if err := migrateSchema(db); err != nil {
		t.Fatalf("failed to migrate schema: %v", err)
	}

	// 2. Pre-create a table A05 for branch-001
	_, err = db.Exec(`
		INSERT INTO room_tables (table_id, branch_id, name_number, capacity, status, is_active)
		VALUES ('tbl-a05', 'branch-001', 'A05', 4, 'AVAILABLE', 1)
	`)
	if err != nil {
		t.Fatalf("failed to insert test table: %v", err)
	}

	listener := NewQROrderListener(db, &QROrderConfig{
		BranchID:  "branch-001",
		ActiveKey: "KEY-TEST-123",
	})

	order := &IncomingCloudOrder{
		OrderID:      "cloud-qr-ord-999",
		BranchID:     "branch-001",
		TableNumber:  "A05",
		CustomerNote: "ขอช้อนส้อมเด็ก 1 ชุด",
		TotalAmount:  420.00,
		CreatedAt:    "2026-09-03T10:30:00Z",
		Items: []IncomingCloudOrderItem{
			{
				ProductID:   "P-Kurobuta",
				ProductName: "หมูคุโรบูตะสันคอ",
				Quantity:    1,
				UnitPrice:   240.00,
				Note:        "ขอไม่มัน",
			},
			{
				ProductID:   "P-FriedRice",
				ProductName: "ข้าวผัดกระเทียม",
				Quantity:    2,
				UnitPrice:   90.00,
			},
		},
	}

	// 3. Process & Save QR order (triggers ConvertQROrderToRoomOrderTx)
	if err := listener.SaveOrderToSQLite(order); err != nil {
		t.Fatalf("SaveOrderToSQLite failed: %v", err)
	}

	// 4. Verify qr_orders has pos_order_id populated
	var posOrderID string
	err = db.QueryRow("SELECT pos_order_id FROM qr_orders WHERE cloud_order_id = ?", order.OrderID).Scan(&posOrderID)
	if err != nil || posOrderID == "" {
		t.Fatalf("expected pos_order_id in qr_orders, got err: %v, pos_order_id: %s", err, posOrderID)
	}

	// 5. Verify room_orders created with channel = 'QR'
	var channel, status, kitchenStatus, orderNumber, tableID, tableSessionID string
	var totalAmountSatang int64
	err = db.QueryRow(`
		SELECT channel, status, kitchen_status, order_number, table_id, table_session_id, total_amount
		FROM room_orders 
		WHERE order_id = ?
	`, posOrderID).Scan(&channel, &status, &kitchenStatus, &orderNumber, &tableID, &tableSessionID, &totalAmountSatang)

	if err != nil {
		t.Fatalf("failed to query room_orders: %v", err)
	}

	if channel != "QR" {
		t.Errorf("expected channel 'QR', got: %s", channel)
	}
	if status != "IN_KITCHEN" {
		t.Errorf("expected status 'IN_KITCHEN', got: %s", status)
	}
	if kitchenStatus != "SENT" {
		t.Errorf("expected kitchenStatus 'SENT', got: %s", kitchenStatus)
	}
	if tableID != "tbl-a05" {
		t.Errorf("expected tableID 'tbl-a05', got: %s", tableID)
	}
	if tableSessionID == "" {
		t.Errorf("expected non-empty tableSessionID")
	}
	// Total amount in Satang: 240 + 2*90 = 420 THB = 42000 Satang
	if totalAmountSatang != 42000 {
		t.Errorf("expected totalAmountSatang 42000, got: %d", totalAmountSatang)
	}

	// 6. Verify room_order_items created
	var itemCount int
	err = db.QueryRow("SELECT COUNT(*) FROM room_order_items WHERE order_id = ?", posOrderID).Scan(&itemCount)
	if err != nil || itemCount != 2 {
		t.Fatalf("expected 2 items in room_order_items, got: %d", itemCount)
	}

	// 7. Verify table status updated to OCCUPIED
	var tblStatus string
	err = db.QueryRow("SELECT status FROM room_tables WHERE table_id = ?", tableID).Scan(&tblStatus)
	if err != nil || tblStatus != "OCCUPIED" {
		t.Errorf("expected table status 'OCCUPIED', got: %s", tblStatus)
	}

	// 8. Test POS Active Orders Query (HandleGetActiveOrders simulation)
	var activeOrderCount int
	err = db.QueryRow(`
		SELECT COUNT(*) FROM room_orders 
		WHERE status IN ('OPEN', 'IN_KITCHEN', 'WAITING_PAYMENT') AND branch_id = 'branch-001'
	`).Scan(&activeOrderCount)
	if err != nil || activeOrderCount != 1 {
		t.Errorf("expected 1 active order in POS cashier view, got: %d", activeOrderCount)
	}

	// 9. Idempotency Test: re-saving identical order must NOT duplicate room_orders
	if err := listener.SaveOrderToSQLite(order); err != nil {
		t.Fatalf("second SaveOrderToSQLite call failed: %v", err)
	}

	var roomOrderCount int
	err = db.QueryRow("SELECT COUNT(*) FROM room_orders WHERE cloud_order_id = ?", order.OrderID).Scan(&roomOrderCount)
	if err != nil || roomOrderCount != 1 {
		t.Errorf("expected exactly 1 room_order after re-saving, got: %d", roomOrderCount)
	}
}
