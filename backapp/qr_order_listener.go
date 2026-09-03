package main

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

// QROrderConfig holds branch connection settings to Cloud
type QROrderConfig struct {
	CloudWSURL   string        `json:"cloudWsUrl"`   // e.g. "ws://localhost:8080/ws"
	CloudHTTPURL string        `json:"cloudHttpUrl"` // e.g. "http://localhost:8080"
	BranchID     string        `json:"branchId"`
	ActiveKey    string        `json:"activeKey"`
	PollInterval time.Duration `json:"pollInterval"` // Fallback polling interval
}

// LoadQROrderConfig reads config from file, env vars, or device_identity SQLite table
func LoadQROrderConfig(db *sql.DB) (*QROrderConfig, error) {
	cfg := &QROrderConfig{
		CloudWSURL:   "ws://localhost:8080/ws",
		CloudHTTPURL: "http://localhost:8080",
		BranchID:     "branch-001",
		ActiveKey:    "",
		PollInterval: 30 * time.Second,
	}

	// 1. Read from qr_config.json if exists
	if fileData, err := os.ReadFile("qr_config.json"); err == nil {
		_ = json.Unmarshal(fileData, cfg)
	}

	// 2. Read from Environment Variables (Override)
	if wsURL := os.Getenv("CLOUD_WS_URL"); wsURL != "" {
		cfg.CloudWSURL = wsURL
	}
	if httpURL := os.Getenv("CLOUD_HTTP_URL"); httpURL != "" {
		cfg.CloudHTTPURL = httpURL
	}
	if bID := os.Getenv("POS_BRANCH_ID"); bID != "" {
		cfg.BranchID = bID
	}
	if aKey := os.Getenv("POS_ACTIVE_KEY"); aKey != "" {
		cfg.ActiveKey = aKey
	}

	// 3. Fallback to device_identity table from SQLite if activeKey is empty
	if db != nil && (cfg.ActiveKey == "" || cfg.BranchID == "") {
		if ident, err := GetDeviceIdentity(db); err == nil && ident != nil {
			if cfg.BranchID == "" || cfg.BranchID == "branch-001" {
				cfg.BranchID = ident.BranchID
			}
			if cfg.ActiveKey == "" {
				cfg.ActiveKey = ident.ActivationCode
			}
			if cfg.CloudHTTPURL == "http://localhost:8080" && ident.CloudApiUrl != "" {
				cfg.CloudHTTPURL = strings.TrimSuffix(ident.CloudApiUrl, "/api/v1")
			}
		}
	}

	if cfg.CloudWSURL == "" {
		// Auto derive ws URL from http URL
		u, err := url.Parse(cfg.CloudHTTPURL)
		if err == nil {
			scheme := "ws"
			if u.Scheme == "https" {
				scheme = "wss"
			}
			cfg.CloudWSURL = fmt.Sprintf("%s://%s/ws", scheme, u.Host)
		}
	}

	return cfg, nil
}

// IncomingCloudOrderItem matches Cloud JSON item structure
type IncomingCloudOrderItem struct {
	ProductID   string      `json:"productId"`
	ProductName string      `json:"productName"`
	Quantity    int         `json:"quantity"`
	UnitPrice   float64     `json:"unitPrice"`
	Options     interface{} `json:"options"`
	Note        string      `json:"note"`
}

// IncomingCloudOrder matches Cloud JSON order structure
type IncomingCloudOrder struct {
	OrderID      string                   `json:"orderId"`
	BranchID     string                   `json:"branchId"`
	TableNumber  string                   `json:"tableNumber"`
	CustomerNote string                   `json:"customerNote"`
	TotalAmount  float64                  `json:"totalAmount"`
	Items        []IncomingCloudOrderItem `json:"items"`
	CreatedAt    string                   `json:"createdAt"`
}

// QROrderListener manages the WebSocket lifecycle and fallback poller
type QROrderListener struct {
	db          *sql.DB
	config      *QROrderConfig
	httpClient  *http.Client
	mu          sync.RWMutex
	isConnected bool
	stopChan    chan struct{}
}

// NewQROrderListener initializes the listener
func NewQROrderListener(db *sql.DB, cfg *QROrderConfig) *QROrderListener {
	return &QROrderListener{
		db:         db,
		config:     cfg,
		httpClient: &http.Client{Timeout: 10 * time.Second},
		stopChan:   make(chan struct{}),
	}
}

// Start launches the WebSocket loop and fallback polling routine
func (l *QROrderListener) Start() {
	log.Printf("[QROrder] Starting listener for Branch: %s (Cloud WS: %s)", l.config.BranchID, l.config.CloudWSURL)

	// Goroutine 1: WebSocket client with auto-reconnect
	go l.runWebSocketLoop()

	// Goroutine 2: Fallback poller in case WebSocket is down
	go l.runFallbackPoller()
}

// Stop gracefully shuts down listener
func (l *QROrderListener) Stop() {
	close(l.stopChan)
}

// runWebSocketLoop connects to Cloud STOMP broker and auto-reconnects with exponential backoff
func (l *QROrderListener) runWebSocketLoop() {
	backoff := 2 * time.Second
	maxBackoff := 30 * time.Second

	for {
		select {
		case <-l.stopChan:
			return
		default:
		}

		err := l.connectAndListen()
		l.mu.Lock()
		l.isConnected = false
		l.mu.Unlock()

		if err != nil {
			log.Printf("[QROrder] WebSocket connection lost / failed: %v", err)
		}

		// Calculate backoff with jitter
		jitter := time.Duration(rand.Int63n(int64(1 * time.Second)))
		sleepDuration := backoff + jitter
		log.Printf("[QROrder] Reconnecting in %v...", sleepDuration)

		select {
		case <-time.After(sleepDuration):
		case <-l.stopChan:
			return
		}

		backoff *= 2
		if backoff > maxBackoff {
			backoff = maxBackoff
		}
	}
}

// connectAndListen establishes WebSocket + STOMP connection with Heartbeat negotiation and silence detection
func (l *QROrderListener) connectAndListen() error {
	headers := make(http.Header)
	headers.Set("branchId", l.config.BranchID)
	headers.Set("activeKey", l.config.ActiveKey)

	dialer := websocket.Dialer{
		HandshakeTimeout: 10 * time.Second,
	}

	log.Printf("[QROrder] Connecting to Cloud WebSocket: %s (Branch: %s)...", l.config.CloudWSURL, l.config.BranchID)
	ws, resp, err := dialer.Dial(l.config.CloudWSURL, headers)
	if err != nil {
		if resp != nil {
			return fmt.Errorf("handshake failed with status %d: %w", resp.StatusCode, err)
		}
		return fmt.Errorf("dial error: %w", err)
	}
	defer ws.Close()

	var writeMu sync.Mutex

	// 1. Send STOMP CONNECT Frame (Propose 10s client outgoing, 10s client incoming)
	connectFrame := fmt.Sprintf(
		"CONNECT\naccept-version:1.1,1.2\nheart-beat:10000,10000\nbranchId:%s\nactiveKey:%s\n\n\x00",
		l.config.BranchID, l.config.ActiveKey,
	)

	writeMu.Lock()
	_ = ws.SetWriteDeadline(time.Now().Add(5 * time.Second))
	err = ws.WriteMessage(websocket.TextMessage, []byte(connectFrame))
	writeMu.Unlock()
	if err != nil {
		return fmt.Errorf("failed to send STOMP CONNECT frame: %w", err)
	}

	// 2. Read STOMP CONNECTED Frame
	_ = ws.SetReadDeadline(time.Now().Add(10 * time.Second))
	_, msg, err := ws.ReadMessage()
	if err != nil {
		return fmt.Errorf("failed to read response to STOMP CONNECT: %w", err)
	}

	msgStr := string(msg)
	if strings.HasPrefix(msgStr, "ERROR") {
		return fmt.Errorf("cloud rejected STOMP CONNECT: %s", msgStr)
	}
	if !strings.HasPrefix(msgStr, "CONNECTED") {
		return fmt.Errorf("unexpected STOMP frame received: %s", msgStr)
	}

	l.mu.Lock()
	l.isConnected = true
	l.mu.Unlock()
	log.Printf("🟢 [QROrder] STOMP Authenticated & Connected successfully to Cloud!")

	// 3. Negotiate Heartbeat parameters
	serverSx, serverSy := parseHeartbeatHeader(msgStr)
	// Outgoing client heartbeat interval = max(client_cx, server_sy)
	sendInterval := 10 * time.Second
	if serverSy > 0 {
		intervalMs := 10000
		if serverSy > intervalMs {
			intervalMs = serverSy
		} else if serverSy >= 50 {
			// Allow lower interval for testing/rapid heartbeats
			intervalMs = serverSy
		}
		sendInterval = time.Duration(intervalMs) * time.Millisecond
	}

	// Expected incoming interval from server = max(client_cy, server_sx)
	expectedIncoming := 10 * time.Second
	if serverSx > 0 {
		intervalMs := 10000
		if serverSx > intervalMs {
			intervalMs = serverSx
		} else if serverSx >= 50 {
			intervalMs = serverSx
		}
		expectedIncoming = time.Duration(intervalMs) * time.Millisecond
	}

	// Silence detection timeout allows a 2.5x grace multiplier for network jitter
	readTimeout := expectedIncoming * 25 / 10
	log.Printf("💓 [QROrder] STOMP Heartbeat active (Send every: %v, Expect receive every: %v, Read Timeout: %v)",
		sendInterval, expectedIncoming, readTimeout)

	// 4. Send STOMP SUBSCRIBE Frame
	subscribeTopic := fmt.Sprintf("/topic/branch/%s/orders", l.config.BranchID)
	subFrame := fmt.Sprintf(
		"SUBSCRIBE\nid:sub-qr-orders\ndestination:%s\nack:auto\n\n\x00",
		subscribeTopic,
	)
	writeMu.Lock()
	_ = ws.SetWriteDeadline(time.Now().Add(5 * time.Second))
	err = ws.WriteMessage(websocket.TextMessage, []byte(subFrame))
	writeMu.Unlock()
	if err != nil {
		return fmt.Errorf("failed to subscribe to %s: %w", subscribeTopic, err)
	}
	log.Printf("📡 [QROrder] Subscribed to topic: %s", subscribeTopic)

	// 5. Start Client Heartbeat Sender Goroutine (Clean Lifecyle via sessionDone)
	sessionDone := make(chan struct{})
	defer close(sessionDone)

	if sendInterval > 0 {
		go func() {
			ticker := time.NewTicker(sendInterval)
			defer ticker.Stop()

			for {
				select {
				case <-sessionDone:
					return
				case <-l.stopChan:
					return
				case <-ticker.C:
					writeMu.Lock()
					_ = ws.SetWriteDeadline(time.Now().Add(5 * time.Second))
					// STOMP heartbeat ping is a single newline byte '\n'
					err := ws.WriteMessage(websocket.TextMessage, []byte("\n"))
					writeMu.Unlock()
					if err != nil {
						log.Printf("⚠️ [QROrder-Heartbeat] Failed to send heartbeat to server: %v", err)
						_ = ws.Close() // Force read loop to exit and trigger reconnect
						return
					}
				}
			}
		}()
	}

	// 6. Message Reading Loop with Heartbeat & Silence Detection
	for {
		select {
		case <-l.stopChan:
			return nil
		default:
		}

		if readTimeout > 0 {
			_ = ws.SetReadDeadline(time.Now().Add(readTimeout))
		}

		_, payload, err := ws.ReadMessage()
		if err != nil {
			return fmt.Errorf("connection read error (possible silence/timeout): %w", err)
		}

		// Refresh read deadline upon receiving any byte
		if readTimeout > 0 {
			_ = ws.SetReadDeadline(time.Now().Add(readTimeout))
		}

		rawText := strings.TrimSpace(string(payload))
		if rawText == "" {
			// Received STOMP heartbeat (single '\n' or '\r\n') from server
			// Connection is healthy; continue listening
			continue
		}

		if strings.HasPrefix(rawText, "MESSAGE") {
			body := extractStompBody(string(payload))
			if body != "" {
				l.handleIncomingOrder([]byte(body))
			}
		} else if strings.HasPrefix(rawText, "ERROR") {
			return fmt.Errorf("received STOMP ERROR: %s", rawText)
		}
	}
}

// parseHeartbeatHeader extracts sx,sy from "heart-beat:sx,sy" header in CONNECTED frame
func parseHeartbeatHeader(frame string) (int, int) {
	lines := strings.Split(frame, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		lower := strings.ToLower(line)
		if strings.HasPrefix(lower, "heart-beat:") {
			val := strings.TrimPrefix(lower, "heart-beat:")
			parts := strings.Split(val, ",")
			if len(parts) == 2 {
				var sx, sy int
				_, _ = fmt.Sscanf(strings.TrimSpace(parts[0]), "%d", &sx)
				_, _ = fmt.Sscanf(strings.TrimSpace(parts[1]), "%d", &sy)
				return sx, sy
			}
		}
	}
	return 10000, 10000 // Default fallback 10s, 10s
}

// extractStompBody parses the JSON payload from a STOMP frame
func extractStompBody(raw string) string {
	parts := strings.SplitN(raw, "\n\n", 2)
	if len(parts) < 2 {
		return ""
	}
	body := strings.TrimRight(parts[1], "\x00\r\n ")
	return body
}

// handleIncomingOrder processes an order received from Cloud
func (l *QROrderListener) handleIncomingOrder(rawJSON []byte) {
	var order IncomingCloudOrder
	if err := json.Unmarshal(rawJSON, &order); err != nil {
		log.Printf("[QROrder] Error parsing order JSON: %v. Raw: %s", err, string(rawJSON))
		return
	}

	log.Printf("🔔 [QROrder] Received Order [%s] for Table %s (Amount: ฿%.2f, Items: %d)",
		order.OrderID, order.TableNumber, order.TotalAmount, len(order.Items))

	// 1. Save to SQLite via Transaction
	if err := l.SaveOrderToSQLite(&order); err != nil {
		log.Printf("❌ [QROrder] Failed to save order to SQLite: %v", err)
		return
	}

	// 2. Send ACK back to Cloud
	go l.SendOrderACK(order.OrderID)
}

// SaveOrderToSQLite writes order and order items atomically using a SQL transaction
func (l *QROrderListener) SaveOrderToSQLite(order *IncomingCloudOrder) error {
	tx, err := l.db.Begin()
	if err != nil {
		return fmt.Errorf("begin tx error: %w", err)
	}
	defer tx.Rollback()

	nowStr := time.Now().Format(time.RFC3339)
	localID := uuid.New().String()

	// 1. Insert or Ignore Parent Table: qr_orders
	orderQuery := `
		INSERT INTO qr_orders (
			id, cloud_order_id, branch_id, table_number, status, customer_note,
			total_amount, source, print_status, synced_at, created_at, updated_at
		) VALUES (?, ?, ?, ?, 'received', ?, ?, 'qr', 'pending', ?, ?, ?)
		ON CONFLICT(cloud_order_id) DO UPDATE SET
			status = 'received',
			updated_at = excluded.updated_at
	`
	_, err = tx.Exec(
		orderQuery,
		localID, order.OrderID, order.BranchID, order.TableNumber, order.CustomerNote,
		order.TotalAmount, nowStr, order.CreatedAt, nowStr,
	)
	if err != nil {
		return fmt.Errorf("insert qr_orders error: %w", err)
	}

	// Fetch the actual local ID if it was an on conflict update
	var actualID string
	_ = tx.QueryRow("SELECT id FROM qr_orders WHERE cloud_order_id = ?", order.OrderID).Scan(&actualID)
	if actualID != "" {
		localID = actualID
	}

	// 2. Insert Child Table: qr_order_items
	itemQuery := `
		INSERT OR IGNORE INTO qr_order_items (
			id, order_id, product_id, product_name, quantity, unit_price, options, note
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`

	for _, item := range order.Items {
		var optStr string
		if item.Options != nil {
			if s, ok := item.Options.(string); ok {
				optStr = s
			} else {
				if b, err := json.Marshal(item.Options); err == nil {
					optStr = string(b)
				}
			}
		}

		itemID := uuid.New().String()
		_, err = tx.Exec(
			itemQuery,
			itemID, localID, item.ProductID, item.ProductName, item.Quantity,
			item.UnitPrice, optStr, item.Note,
		)
		if err != nil {
			return fmt.Errorf("insert qr_order_items error: %w", err)
		}
	}

	// 3. Automatically transform QR order into main POS room_order (Channel: QR)
	posOrderID, err := ConvertQROrderToRoomOrderTx(tx, order)
	if err != nil {
		log.Printf("⚠️ [QROrder-POS] Failed to convert QR order to room_order: %v", err)
	} else if posOrderID != "" {
		_, _ = tx.Exec("UPDATE qr_orders SET pos_order_id = ? WHERE cloud_order_id = ?", posOrderID, order.OrderID)
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit tx error: %w", err)
	}

	log.Printf("💾 [QROrder] Order [%s] saved to SQLite successfully (Print status: pending, POS Order ID: %s)", order.OrderID, posOrderID)

	// Trigger immediate kitchen thermal print in background
	go func(oID string) {
		printerCfg := LoadPrinterConfig()
		if err := PrintKitchenOrder(l.db, printerCfg, oID, false); err != nil {
			log.Printf("⚠️ [QROrder] Immediate kitchen print failed for Order [%s]: %v", oID, err)
		}
	}(order.OrderID)

	return nil
}

// SendOrderACK sends ACK to Cloud at POST /api/internal/orders/{orderId}/ack
func (l *QROrderListener) SendOrderACK(orderID string) {
	ackURL := fmt.Sprintf("%s/api/internal/orders/%s/ack", strings.TrimRight(l.config.CloudHTTPURL, "/"), orderID)
	req, err := http.NewRequest("POST", ackURL, bytes.NewBuffer(nil))
	if err != nil {
		log.Printf("[QROrder] ACK request build failed: %v", err)
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("branchId", l.config.BranchID)
	req.Header.Set("activeKey", l.config.ActiveKey)

	resp, err := l.httpClient.Do(req)
	if err != nil {
		log.Printf("[QROrder] Failed to send ACK for order [%s]: %v", orderID, err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusOK {
		log.Printf("✅ [QROrder] ACK confirmed by Cloud for Order [%s]", orderID)
	} else {
		body, _ := io.ReadAll(resp.Body)
		log.Printf("⚠️ [QROrder] Cloud returned status %d for ACK: %s", resp.StatusCode, string(body))
	}
}

// runFallbackPoller polls Cloud periodically if WebSocket is disconnected or as a safety net
func (l *QROrderListener) runFallbackPoller() {
	ticker := time.NewTicker(l.config.PollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-l.stopChan:
			return
		case <-ticker.C:
			// Check if we need to poll
			l.mu.RLock()
			online := l.isConnected
			l.mu.RUnlock()

			// If disconnected, or as a safety net every interval
			l.pollPendingOrders(online)
		}
	}
}

// pollPendingOrders fetches pending orders from Cloud GET /api/v1/qr/branch/{branchId}/pending
func (l *QROrderListener) pollPendingOrders(isWSOnline bool) {
	pollURL := fmt.Sprintf("%s/api/v1/qr/branch/%s/pending", strings.TrimRight(l.config.CloudHTTPURL, "/"), l.config.BranchID)

	req, err := http.NewRequest("GET", pollURL, nil)
	if err != nil {
		return
	}
	req.Header.Set("branchId", l.config.BranchID)
	req.Header.Set("activeKey", l.config.ActiveKey)

	resp, err := l.httpClient.Do(req)
	if err != nil {
		if !isWSOnline {
			log.Printf("[QROrder-Poller] Cloud unreachable: %v", err)
		}
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return
	}

	var responseWrapper struct {
		Success bool `json:"success"`
		Data    []struct {
			Order struct {
				ID           string  `json:"id"`
				BranchID     string  `json:"branchId"`
				TableNumber  string  `json:"tableNumber"`
				CustomerNote string  `json:"customerNote"`
				TotalAmount  float64 `json:"totalAmount"`
				CreatedAt    string  `json:"createdAt"`
			} `json:"order"`
			Items []IncomingCloudOrderItem `json:"items"`
		} `json:"data"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&responseWrapper); err != nil {
		return
	}

	if len(responseWrapper.Data) > 0 {
		log.Printf("[QROrder-Poller] Found %d pending orders via polling", len(responseWrapper.Data))
		for _, item := range responseWrapper.Data {
			order := IncomingCloudOrder{
				OrderID:      item.Order.ID,
				BranchID:     item.Order.BranchID,
				TableNumber:  item.Order.TableNumber,
				CustomerNote: item.Order.CustomerNote,
				TotalAmount:  item.Order.TotalAmount,
				Items:        item.Items,
				CreatedAt:    item.Order.CreatedAt,
			}
			if err := l.SaveOrderToSQLite(&order); err == nil {
				go l.SendOrderACK(order.OrderID)
			}
		}
	}
}

// StartQROrderListener is a convenience launcher called by supervisor / main
func StartQROrderListener(db *sql.DB) *QROrderListener {
	cfg, err := LoadQROrderConfig(db)
	if err != nil {
		log.Printf("[QROrder] Failed to load config: %v", err)
		return nil
	}

	listener := NewQROrderListener(db, cfg)
	listener.Start()
	return listener
}
