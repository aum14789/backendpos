package main

import (
	"database/sql"
	"strings"
	"testing"
	"time"

	_ "modernc.org/sqlite"
)

func TestBuildKitchenSlip(t *testing.T) {
	order := &OrderForPrint{
		ID:           "loc-ord-123",
		CloudOrderID: "c9876543-abcd-ef01-2345",
		TableNumber:  "5",
		CustomerNote: "ไม่ใส่ผักชี",
		CreatedAt:    "2026-09-03T10:15:30Z",
		Items: []ItemForPrint{
			{
				ProductName: "ข้าวผัดกุ้ง",
				Quantity:    2,
				Note:        "ขอพริกน้ำปลาด้วย",
			},
			{
				ProductName: "ต้มยำกุ้งแม่น้ำ",
				Quantity:    1,
				Note:        "เผ็ดน้อย",
			},
		},
	}

	// 1. Build normal slip
	slipBytes := BuildKitchenSlip(order, false, 24, false)
	slipText := string(slipBytes)

	if !strings.Contains(slipText, "โต๊ะ: 5") {
		t.Errorf("expected table number 5 in slip, got: %s", slipText)
	}
	if !strings.Contains(slipText, "2x ข้าวผัดกุ้ง") {
		t.Errorf("expected 2x ข้าวผัดกุ้ง, got: %s", slipText)
	}
	if !strings.Contains(slipText, "หมายเหตุ: ขอพริกน้ำปลาด้วย") {
		t.Errorf("expected item note, got: %s", slipText)
	}
	if !strings.Contains(slipText, "หมายเหตุรวม: ไม่ใส่ผักชี") {
		t.Errorf("expected customer note, got: %s", slipText)
	}
	if strings.Contains(slipText, "พิมพ์ซ้ำ") {
		t.Errorf("normal slip should not contain reprint header")
	}

	// 2. Build reprint slip
	reprintBytes := BuildKitchenSlip(order, true, 24, false)
	reprintText := string(reprintBytes)
	if !strings.Contains(reprintText, "พิมพ์ซ้ำ / REPRINT") {
		t.Errorf("reprint slip should contain reprint header, got: %s", reprintText)
	}
}

func TestUpdatePrintStatus(t *testing.T) {
	db, err := sql.Open("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open memory db: %v", err)
	}
	defer db.Close()

	if err := migrateSchema(db); err != nil {
		t.Fatalf("failed to migrate schema: %v", err)
	}

	nowStr := time.Now().Format(time.RFC3339)
	orderID := "test-order-print-01"

	// Seed test order
	_, err = db.Exec(`
		INSERT INTO qr_orders (id, cloud_order_id, branch_id, table_number, status, print_status, total_amount, created_at, updated_at)
		VALUES (?, ?, 'BR001', '3', 'received', 'pending', 150.0, ?, ?)
	`, orderID, orderID, nowStr, nowStr)
	if err != nil {
		t.Fatalf("failed to seed order: %v", err)
	}

	// 1. Mark as printed
	updatePrintStatus(db, orderID, "printed")

	var status, printedAt sql.NullString
	err = db.QueryRow("SELECT print_status, printed_at FROM qr_orders WHERE id = ?", orderID).Scan(&status, &printedAt)
	if err != nil {
		t.Fatalf("failed to query status: %v", err)
	}
	if status.String != "printed" || !printedAt.Valid {
		t.Errorf("expected status 'printed' with valid printed_at, got: %v, %v", status, printedAt)
	}

	// 2. Mark as failed
	updatePrintStatus(db, orderID, "failed")
	err = db.QueryRow("SELECT print_status FROM qr_orders WHERE id = ?", orderID).Scan(&status)
	if err != nil {
		t.Fatalf("failed to query status: %v", err)
	}
	if status.String != "failed" {
		t.Errorf("expected status 'failed', got: %v", status)
	}
}
