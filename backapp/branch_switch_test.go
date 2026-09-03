package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

func setupTestDB(t *testing.T) {
	testDBPath := "test_pos.db"
	_ = os.Remove(testDBPath)
	_ = os.Remove(testDBPath + "-shm")
	_ = os.Remove(testDBPath + "-wal")

	db, err := InitDB(testDBPath)
	if err != nil {
		t.Fatalf("InitDB error: %v", err)
	}

	if err := SeedDatabase(db); err != nil {
		t.Fatalf("SeedDatabase error: %v", err)
	}
}

func cleanupTestDB() {
	if DB != nil {
		_ = DB.Close()
	}
	_ = os.Remove("test_pos.db")
	_ = os.Remove("test_pos.db-shm")
	_ = os.Remove("test_pos.db-wal")
}

func TestBranchSwitchingAndFirebaseCloudSyncIsolation(t *testing.T) {
	setupTestDB(t)
	defer cleanupTestDB()

	syncEngine := InitSyncEngine()

	// 1. Simulate Firebase / Cloud sync pulling real branch catalog for Branch 1 & Branch 2
	branch1Data := SyncPullResponse{
		Branch: &Branch{
			BranchID:  "branch-001",
			CompanyID: "comp-001",
			Name:      "Sukhumvit Main Branch",
			Code:      "SUK-01",
			TaxRate:   7.0,
		},
		Zones: []Zone{
			{ZoneID: "z-suk-01", BranchID: "branch-001", Name: "Main Dining", ZoneType: "DINE_IN", SortOrder: 1, IsActive: true},
		},
		Tables: []Table{
			{TableID: "t-suk-01", BranchID: "branch-001", ZoneID: "z-suk-01", NameNumber: "11", Capacity: 4, Status: "AVAILABLE", IsActive: true},
			{TableID: "t-suk-02", BranchID: "branch-001", ZoneID: "z-suk-01", NameNumber: "12", Capacity: 4, Status: "AVAILABLE", IsActive: true},
		},
		Categories: []MenuCategory{
			{CategoryID: "c-suk-01", BranchID: "branch-001", Name: "อาหารจานหลัก", SortOrder: 1, IsActive: true},
		},
		MenuItems: []MenuItem{
			{ItemID: "m-suk-01", BranchID: "branch-001", CategoryID: "c-suk-01", Name: "ข้าวผัดปูพิเศษ", BasePrice: 12000, Availability: "AVAILABLE", SortOrder: 1, IsActive: true},
		},
		Users: []User{
			{UserID: "usr-admin", CompanyID: "comp-001", Username: "admin", FullName: "Manager", PinHash: "1234", IsActive: true, Permissions: []string{"ORDER_VIEW", "ORDER_CREATE"}},
		},
	}

	branch2Data := SyncPullResponse{
		Branch: &Branch{
			BranchID:  "branch-002",
			CompanyID: "comp-001",
			Name:      "Siam Paragon Branch",
			Code:      "SIAM-01",
			TaxRate:   7.0,
		},
		Zones: []Zone{
			{ZoneID: "z-siam-01", BranchID: "branch-002", Name: "VIP Zone", ZoneType: "DINE_IN", SortOrder: 1, IsActive: true},
		},
		Tables: []Table{
			{TableID: "t-siam-01", BranchID: "branch-002", ZoneID: "z-siam-01", NameNumber: "VIP-1", Capacity: 8, Status: "AVAILABLE", IsActive: true},
		},
		Categories: []MenuCategory{
			{CategoryID: "c-siam-01", BranchID: "branch-002", Name: "เซ็ตพรีเมียม", SortOrder: 1, IsActive: true},
		},
		MenuItems: []MenuItem{
			{ItemID: "m-siam-01", BranchID: "branch-002", CategoryID: "c-siam-01", Name: "วากิว A5 พรีเมียม", BasePrice: 59000, Availability: "AVAILABLE", SortOrder: 1, IsActive: true},
		},
	}

	// Upsert master catalog from Firebase sync
	syncEngine.upsertMasterCatalog(branch1Data)
	syncEngine.upsertMasterCatalog(branch2Data)

	// 2. Activate POS with Branch 1 Active Key
	actReq1 := ActivateDeviceRequest{ActivationCode: "SUN-SUK01-1111"}
	body1, _ := json.Marshal(actReq1)
	req1 := httptest.NewRequest(http.MethodPost, "/api/device/activate", bytes.NewReader(body1))
	w1 := httptest.NewRecorder()
	HandleActivateDevice(w1, req1)

	if w1.Code != http.StatusOK {
		t.Fatalf("Failed to activate Branch 1: %s", w1.Body.String())
	}

	// Verify Branch 1 only sees Branch 1 tables & menus
	reqTables1 := httptest.NewRequest(http.MethodGet, "/api/tables", nil)
	wTables1 := httptest.NewRecorder()
	HandleGetTables(wTables1, reqTables1)
	var tablesResp1 ApiResponse[[]Table]
	_ = json.NewDecoder(wTables1.Body).Decode(&tablesResp1)

	if len(tablesResp1.Data) != 2 {
		t.Fatalf("Expected 2 tables in Branch 1 from Firebase, got %d", len(tablesResp1.Data))
	}
	for _, tbl := range tablesResp1.Data {
		if tbl.BranchID != "branch-001" {
			t.Errorf("Leaked table from other branch in Branch 1: %s (%s)", tbl.TableID, tbl.BranchID)
		}
	}

	// Place order in Branch 1 on Table 11
	orderReq1 := CreateOrderRequest{
		BranchID:  "branch-001",
		TableID:   &tablesResp1.Data[0].TableID,
		OrderType: "DINE_IN",
		Channel:   "POS",
		CreatedBy: "admin",
		Items: []CreateOrderItem{
			{
				MenuItemID:        "m-suk-01",
				NameSnapshot:      "ข้าวผัดปูพิเศษ",
				UnitPriceSnapshot: 12000,
				Quantity:          1,
			},
		},
	}
	bodyOrder1, _ := json.Marshal(orderReq1)
	reqOrd1 := httptest.NewRequest(http.MethodPost, "/api/orders", bytes.NewReader(bodyOrder1))
	wOrd1 := httptest.NewRecorder()
	HandleCreateOrder(wOrd1, reqOrd1)
	if wOrd1.Code != http.StatusOK {
		t.Fatalf("Failed to create order in Branch 1: %s", wOrd1.Body.String())
	}

	// Verify Branch 1 has 1 active order
	reqActOrd1 := httptest.NewRequest(http.MethodGet, "/api/orders/active", nil)
	wActOrd1 := httptest.NewRecorder()
	HandleGetActiveOrders(wActOrd1, reqActOrd1)
	var actOrdResp1 ApiResponse[[]Order]
	_ = json.NewDecoder(wActOrd1.Body).Decode(&actOrdResp1)
	if len(actOrdResp1.Data) != 1 {
		t.Errorf("Expected 1 active order in Branch 1, got %d", len(actOrdResp1.Data))
	}

	// 3. Switch to Branch 2 (Siam Paragon) Active Key
	actReq2 := ActivateDeviceRequest{ActivationCode: "SUN-SUK02-2222"}
	body2, _ := json.Marshal(actReq2)
	req2 := httptest.NewRequest(http.MethodPost, "/api/device/activate", bytes.NewReader(body2))
	w2 := httptest.NewRecorder()
	HandleActivateDevice(w2, req2)
	if w2.Code != http.StatusOK {
		t.Fatalf("Failed to activate Branch 2: %s", w2.Body.String())
	}

	// Verify Branch 2 only sees Branch 2 tables & menus
	reqTables2 := httptest.NewRequest(http.MethodGet, "/api/tables", nil)
	wTables2 := httptest.NewRecorder()
	HandleGetTables(wTables2, reqTables2)
	var tablesResp2 ApiResponse[[]Table]
	_ = json.NewDecoder(wTables2.Body).Decode(&tablesResp2)
	if len(tablesResp2.Data) != 1 || tablesResp2.Data[0].TableID != "t-siam-01" {
		t.Fatalf("Expected 1 Siam table in Branch 2, got %d", len(tablesResp2.Data))
	}

	// Verify Branch 2 has 0 active orders (Branch 1's order is completely isolated!)
	reqActOrd2 := httptest.NewRequest(http.MethodGet, "/api/orders/active", nil)
	wActOrd2 := httptest.NewRecorder()
	HandleGetActiveOrders(wActOrd2, reqActOrd2)
	var actOrdResp2 ApiResponse[[]Order]
	_ = json.NewDecoder(wActOrd2.Body).Decode(&actOrdResp2)
	if len(actOrdResp2.Data) != 0 {
		t.Errorf("Branch 2 should have 0 active orders, but got %d", len(actOrdResp2.Data))
	}

	// 4. Switch back to Branch 1
	req1Re := httptest.NewRequest(http.MethodPost, "/api/device/activate", bytes.NewReader(body1))
	w1Re := httptest.NewRecorder()
	HandleActivateDevice(w1Re, req1Re)
	if w1Re.Code != http.StatusOK {
		t.Fatalf("Failed to reactivate Branch 1: %s", w1Re.Body.String())
	}

	// Verify Branch 1's active order is still preserved in SQLite
	reqActOrd1Re := httptest.NewRequest(http.MethodGet, "/api/orders/active", nil)
	wActOrd1Re := httptest.NewRecorder()
	HandleGetActiveOrders(wActOrd1Re, reqActOrd1Re)
	var actOrdResp1Re ApiResponse[[]Order]
	_ = json.NewDecoder(wActOrd1Re.Body).Decode(&actOrdResp1Re)
	if len(actOrdResp1Re.Data) != 1 {
		t.Errorf("Expected 1 preserved active order in Branch 1, got %d", len(actOrdResp1Re.Data))
	}
}

func TestMoveTableOrderAndBuffetSessionTransfer(t *testing.T) {
	setupTestDB(t)
	defer cleanupTestDB()

	syncEngine := InitSyncEngine()

	branch1Data := SyncPullResponse{
		Branch: &Branch{
			BranchID:  "branch-001",
			CompanyID: "comp-001",
			Name:      "Sukhumvit Main Branch",
			Code:      "SUK-01",
		},
		Tables: []Table{
			{TableID: "t-b01", BranchID: "branch-001", NameNumber: "B-01", Status: "AVAILABLE", IsActive: true},
			{TableID: "t-b02", BranchID: "branch-001", NameNumber: "B-02", Status: "AVAILABLE", IsActive: true},
		},
		MenuItems: []MenuItem{
			{ItemID: "m-01", BranchID: "branch-001", Name: "เนื้อวากิว", BasePrice: 59900, Availability: "AVAILABLE", IsActive: true},
		},
		BuffetTiers: []BuffetTier{
			{TierID: "tier-wagyu", BranchID: "branch-001", Name: "Premium Wagyu Buffet 599", AdultPrice: 59900, ChildPrice: 29900, TimeLimitMinutes: 120, IsActive: true},
		},
	}
	syncEngine.upsertMasterCatalog(branch1Data)

	// Activate branch 1
	actReq1 := ActivateDeviceRequest{ActivationCode: "SUN-SUK01-1111"}
	body1, _ := json.Marshal(actReq1)
	req1 := httptest.NewRequest(http.MethodPost, "/api/device/activate", bytes.NewReader(body1))
	w1 := httptest.NewRecorder()
	HandleActivateDevice(w1, req1)

	// Open Buffet Order on Table B-01 (4 people: 3 Adults, 1 Child)
	b01ID := "t-b01"
	buffetTierID := "tier-wagyu"
	orderReq := CreateOrderRequest{
		BranchID:     "branch-001",
		TableID:      &b01ID,
		OrderType:    "BUFFET",
		Channel:      "POS",
		CreatedBy:    "cashier",
		BuffetTierID: &buffetTierID,
		AdultCount:   3,
		ChildCount:   1,
		Items: []CreateOrderItem{
			{
				MenuItemID:        "m-01",
				NameSnapshot:      "เนื้อวากิว",
				UnitPriceSnapshot: 0,
				Quantity:          4,
				IsBuffetIncluded:  true,
			},
		},
	}
	bodyOrder, _ := json.Marshal(orderReq)
	reqOrd := httptest.NewRequest(http.MethodPost, "/api/orders", bytes.NewReader(bodyOrder))
	wOrd := httptest.NewRecorder()
	HandleCreateOrder(wOrd, reqOrd)

	if wOrd.Code != http.StatusOK {
		t.Fatalf("Failed to create buffet order on B-01: %s", wOrd.Body.String())
	}

	var createOrdResp ApiResponse[Order]
	_ = json.NewDecoder(wOrd.Body).Decode(&createOrdResp)
	createdOrderID := createOrdResp.Data.OrderID

	// Verify B-01 is OCCUPIED/WAITING_FOOD
	var t1Status string
	_ = DB.QueryRow("SELECT status FROM room_tables WHERE table_id = ?", "t-b01").Scan(&t1Status)
	if t1Status != "OCCUPIED" && t1Status != "WAITING_FOOD" {
		t.Errorf("Expected B-01 to be occupied, got %s", t1Status)
	}

	// ── Execute Move Table: B-01 -> B-02 ──
	moveReq := MoveTableRequest{
		SourceTableID: "t-b01",
		TargetTableID: "t-b02",
		Reason:        "ลูกค้าย้ายโต๊ะ",
	}
	bodyMove, _ := json.Marshal(moveReq)
	reqMove := httptest.NewRequest(http.MethodPost, "/api/tables/move", bytes.NewReader(bodyMove))
	wMove := httptest.NewRecorder()
	HandleMoveTable(wMove, reqMove)

	if wMove.Code != http.StatusOK {
		t.Fatalf("Failed to move table B-01 to B-02: %s", wMove.Body.String())
	}

	// 1. Verify B-01 is now AVAILABLE (โต๊ะเดิมต้องว่าง)
	var b01NewStatus string
	_ = DB.QueryRow("SELECT status FROM room_tables WHERE table_id = ?", "t-b01").Scan(&b01NewStatus)
	if b01NewStatus != "AVAILABLE" {
		t.Errorf("Expected source table B-01 to be AVAILABLE after move, got %s", b01NewStatus)
	}

	// 2. Verify B-02 is now OCCUPIED (โต๊ะปลายทางต้องเปิด)
	var b02NewStatus string
	_ = DB.QueryRow("SELECT status FROM room_tables WHERE table_id = ?", "t-b02").Scan(&b02NewStatus)
	if b02NewStatus != "OCCUPIED" && b02NewStatus != "WAITING_FOOD" {
		t.Errorf("Expected target table B-02 to be OCCUPIED after move, got %s", b02NewStatus)
	}

	// 3. Verify the order is now linked to B-02 and has all original items and amount!
	var orderNewTableID string
	var orderNewTotal int64
	_ = DB.QueryRow("SELECT table_id, total_amount FROM room_orders WHERE order_id = ?", createdOrderID).Scan(&orderNewTableID, &orderNewTotal)
	if orderNewTableID != "t-b02" {
		t.Errorf("Expected order to be assigned to table t-b02, got %s", orderNewTableID)
	}
	if orderNewTotal != createOrdResp.Data.TotalAmount {
		t.Errorf("Expected order total amount to remain %d, got %d", createOrdResp.Data.TotalAmount, orderNewTotal)
	}

	// 4. Verify Buffet Session is still intact for this order
	var sessionCount int
	_ = DB.QueryRow("SELECT COUNT(*) FROM room_buffet_sessions WHERE order_id = ?", createdOrderID).Scan(&sessionCount)
	if sessionCount != 1 {
		t.Errorf("Expected 1 buffet session for order %s, got %d", createdOrderID, sessionCount)
	}
}

func TestJWTSecurityAndRateLimiting(t *testing.T) {
	testDB := "test_pos.db"
	_ = os.Remove(testDB)
	InitDB(testDB)
	defer os.Remove(testDB)

	// 1. Test JWT Generation and Verification
	user := User{
		UserID:      "usr-test-01",
		Username:    "cashier_test",
		FullName:    "Test Cashier",
		CompanyID:   "comp-001",
		Permissions: []string{"ORDER_VIEW", "ORDER_CREATE"},
	}
	token, err := GenerateJWT(user, "branch-001", "device-pos-01")
	if err != nil {
		t.Fatalf("Failed to generate JWT: %v", err)
	}
	if token == "" || !strings.Contains(token, ".") {
		t.Fatalf("Invalid JWT token format: %s", token)
	}

	claims, err := VerifyJWT(token)
	if err != nil {
		t.Fatalf("Failed to verify valid JWT: %v", err)
	}
	if claims.UserID != user.UserID || claims.BranchID != "branch-001" {
		t.Errorf("Claims mismatch. Expected user %s, got %s", user.UserID, claims.UserID)
	}

	// 2. Test Invalid Token Verification
	_, err = VerifyJWT(token + "tampered")
	if err == nil {
		t.Errorf("Expected signature verification failure for tampered token")
	}

	// 3. Test Protected Endpoint with JWTMiddleware
	protectedHandler := JWTMiddleware(func(w http.ResponseWriter, r *http.Request) {
		jsonResponse(w, http.StatusOK, map[string]string{"message": "access granted"})
	}, "ORDER_CREATE")

	// Call without token -> 401
	reqNoAuth := httptest.NewRequest(http.MethodGet, "/api/orders", nil)
	wNoAuth := httptest.NewRecorder()
	protectedHandler(wNoAuth, reqNoAuth)
	if wNoAuth.Code != http.StatusUnauthorized {
		t.Errorf("Expected 401 Unauthorized without Bearer token, got %d", wNoAuth.Code)
	}

	// Call with valid token -> 200
	reqAuth := httptest.NewRequest(http.MethodGet, "/api/orders", nil)
	reqAuth.Header.Set("Authorization", "Bearer "+token)
	wAuth := httptest.NewRecorder()
	protectedHandler(wAuth, reqAuth)
	if wAuth.Code != http.StatusOK {
		t.Errorf("Expected 200 OK with valid Bearer token, got %d: %s", wAuth.Code, wAuth.Body.String())
	}

	// 4. Test Rate Limiter
	rl := NewRateLimiter(3, 1*time.Minute, 1*time.Minute)
	testKey := "pos-device-rate-test"
	blocked, rem := rl.RecordFailedAttempt(testKey)
	if blocked || rem != 2 {
		t.Errorf("Expected 2 remaining attempts, got %d", rem)
	}
	rl.RecordFailedAttempt(testKey)
	blocked, rem = rl.RecordFailedAttempt(testKey)
	if !blocked || rem != 0 {
		t.Errorf("Expected key to be blocked on 3rd attempt")
	}
	isBlocked, _ := rl.CheckLimit(testKey)
	if !isBlocked {
		t.Errorf("Expected CheckLimit to report blocked")
	}
}


