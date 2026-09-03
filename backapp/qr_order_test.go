package main

import (
	"database/sql"
	"testing"

	_ "modernc.org/sqlite"
)

func TestSaveOrderToSQLite(t *testing.T) {
	// 1. Shared In-memory test SQLite DB
	db, err := sql.Open("sqlite", "file:mem_qr_test?mode=memory&cache=shared")
	if err != nil {
		t.Fatalf("failed to open memory db: %v", err)
	}
	defer db.Close()

	if err := migrateSchema(db); err != nil {
		t.Fatalf("failed to migrate schema: %v", err)
	}

	listener := NewQROrderListener(db, &QROrderConfig{
		BranchID:  "BR_TEST",
		ActiveKey: "KEY_123",
	})

	order := &IncomingCloudOrder{
		OrderID:      "cloud-ord-001",
		BranchID:     "BR_TEST",
		TableNumber:  "9",
		CustomerNote: "ขอช้อนส้อม 2 คู่",
		TotalAmount:  380.00,
		CreatedAt:    "2026-09-03T10:00:00Z",
		Items: []IncomingCloudOrderItem{
			{
				ProductID:   "P-01",
				ProductName: "ต้มยำกุ้ง",
				Quantity:    1,
				UnitPrice:   250.00,
				Options:     map[string]string{"spicy": "high"},
				Note:        "ไม่ใส่ผักชี",
			},
			{
				ProductID:   "P-02",
				ProductName: "ข้าวสวย",
				Quantity:    2,
				UnitPrice:   65.00,
			},
		},
	}

	// 2. Save Order to SQLite in Transaction
	if err := listener.SaveOrderToSQLite(order); err != nil {
		t.Fatalf("SaveOrderToSQLite failed: %v", err)
	}

	// 3. Verify qr_orders record
	var tableNumber, status, printStatus string
	var totalAmount float64
	err = db.QueryRow("SELECT table_number, status, print_status, total_amount FROM qr_orders WHERE cloud_order_id = ?", order.OrderID).
		Scan(&tableNumber, &status, &printStatus, &totalAmount)
	if err != nil {
		t.Fatalf("failed to query qr_orders: %v", err)
	}

	if tableNumber != "9" || status != "received" || printStatus != "pending" || totalAmount != 380.00 {
		t.Errorf("unexpected order data: table=%s, status=%s, printStatus=%s, total=%.2f",
			tableNumber, status, printStatus, totalAmount)
	}

	// 4. Verify qr_order_items records
	var itemCount int
	err = db.QueryRow("SELECT COUNT(*) FROM qr_order_items WHERE order_id = (SELECT id FROM qr_orders WHERE cloud_order_id = ?)", order.OrderID).
		Scan(&itemCount)
	if err != nil {
		t.Fatalf("failed to query qr_order_items count: %v", err)
	}
	if itemCount != 2 {
		t.Errorf("expected 2 items, got %d", itemCount)
	}

	// 5. Test Idempotency / Duplicate receipt (re-saving same cloud_order_id shouldn't fail)
	if err := listener.SaveOrderToSQLite(order); err != nil {
		t.Errorf("re-saving same order failed: %v", err)
	}
}
