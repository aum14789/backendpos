package main

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"strings"
	"time"
)

// PrinterConfig holds ESC/POS network printer settings
type PrinterConfig struct {
	Enabled       bool   `json:"enabled"`
	PrinterIP     string `json:"printerIp"`     // e.g. "192.168.1.200"
	PrinterPort   int    `json:"printerPort"`   // default 9100
	PaperWidth    int    `json:"paperWidth"`    // 32 chars (58mm) or 42/48 chars (80mm)
	BuzzerEnabled bool   `json:"buzzerEnabled"` // Beep on print
}

// LoadPrinterConfig loads printer config from qr_config.json, env, or defaults
func LoadPrinterConfig() *PrinterConfig {
	cfg := &PrinterConfig{
		Enabled:       true,
		PrinterIP:     "192.168.1.200",
		PrinterPort:   9100,
		PaperWidth:    32, // Default to 32 characters line width
		BuzzerEnabled: true,
	}

	// 1. Read from qr_config.json
	if fileData, err := os.ReadFile("qr_config.json"); err == nil {
		var wrapper struct {
			Printer *PrinterConfig `json:"printer"`
		}
		if err := json.Unmarshal(fileData, &wrapper); err == nil && wrapper.Printer != nil {
			cfg = wrapper.Printer
		}
	}

	// 2. Read from Environment Variables (Override)
	if ip := os.Getenv("KITCHEN_PRINTER_IP"); ip != "" {
		cfg.PrinterIP = ip
	}
	if portStr := os.Getenv("KITCHEN_PRINTER_PORT"); portStr != "" {
		var port int
		if _, err := fmt.Sscanf(portStr, "%d", &port); err == nil && port > 0 {
			cfg.PrinterPort = port
		}
	}
	if os.Getenv("KITCHEN_PRINTER_ENABLED") == "false" {
		cfg.Enabled = false
	}

	return cfg
}

// ESC/POS Commands
var (
	escInit        = []byte{0x1B, 0x40}             // ESC @: Initialize printer
	escAlignLeft   = []byte{0x1B, 0x61, 0x00}       // ESC a 0: Align left
	escAlignCenter = []byte{0x1B, 0x61, 0x01}       // ESC a 1: Align center
	escBoldOn      = []byte{0x1B, 0x45, 0x01}       // ESC E 1: Bold on
	escBoldOff     = []byte{0x1B, 0x45, 0x00}       // ESC E 0: Bold off
	escSizeNormal  = []byte{0x1D, 0x21, 0x00}       // GS ! 0: Normal size
	escSizeLarge   = []byte{0x1D, 0x21, 0x11}       // GS ! 0x11: 2x Width & 2x Height
	escCut         = []byte{0x1D, 0x56, 0x42, 0x00} // GS V B 0: Feed & Partial Cut
	escBeep        = []byte{0x1B, 0x42, 0x02, 0x02} // ESC B 2 2: Sound buzzer 2 times
)

// EscPosBuilder constructs ESC/POS byte sequences
type EscPosBuilder struct {
	buf   bytes.Buffer
	width int
}

// NewEscPosBuilder creates a new ESC/POS byte builder
func NewEscPosBuilder(width int) *EscPosBuilder {
	if width <= 0 {
		width = 32
	}
	b := &EscPosBuilder{width: width}
	b.buf.Write(escInit)
	return b
}

func (b *EscPosBuilder) AlignCenter() *EscPosBuilder {
	b.buf.Write(escAlignCenter)
	return b
}

func (b *EscPosBuilder) AlignLeft() *EscPosBuilder {
	b.buf.Write(escAlignLeft)
	return b
}

func (b *EscPosBuilder) Bold(on bool) *EscPosBuilder {
	if on {
		b.buf.Write(escBoldOn)
	} else {
		b.buf.Write(escBoldOff)
	}
	return b
}

func (b *EscPosBuilder) LargeSize() *EscPosBuilder {
	b.buf.Write(escSizeLarge)
	return b
}

func (b *EscPosBuilder) NormalSize() *EscPosBuilder {
	b.buf.Write(escSizeNormal)
	return b
}

func (b *EscPosBuilder) Line(text string) *EscPosBuilder {
	b.buf.WriteString(text + "\n")
	return b
}

func (b *EscPosBuilder) Divider(char string) *EscPosBuilder {
	if char == "" {
		char = "="
	}
	b.buf.WriteString(strings.Repeat(char, b.width) + "\n")
	return b
}

func (b *EscPosBuilder) Feed(lines int) *EscPosBuilder {
	for i := 0; i < lines; i++ {
		b.buf.WriteByte('\n')
	}
	return b
}

func (b *EscPosBuilder) Beep() *EscPosBuilder {
	b.buf.Write(escBeep)
	return b
}

func (b *EscPosBuilder) Cut() *EscPosBuilder {
	b.Feed(3)
	b.buf.Write(escCut)
	return b
}

func (b *EscPosBuilder) Bytes() []byte {
	return b.buf.Bytes()
}

// SendRawToPrinter sends raw bytes to a network thermal printer over TCP
func SendRawToPrinter(ip string, port int, data []byte) error {
	addr := fmt.Sprintf("%s:%d", ip, port)
	conn, err := net.DialTimeout("tcp", addr, 4*time.Second)
	if err != nil {
		return fmt.Errorf("connect to printer %s failed: %w", addr, err)
	}
	defer conn.Close()

	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := conn.Write(data); err != nil {
		return fmt.Errorf("write to printer %s failed: %w", addr, err)
	}

	return nil
}

// OrderForPrint represents order data loaded from SQLite
type OrderForPrint struct {
	ID           string
	CloudOrderID string
	TableNumber  string
	CustomerNote string
	CreatedAt    string
	PrintStatus  string
	Items        []ItemForPrint
}

type ItemForPrint struct {
	ProductName string
	Quantity    int
	Note        string
	Options     string
}

// BuildKitchenSlip constructs the slip payload exactly matching requirements
func BuildKitchenSlip(order *OrderForPrint, isReprint bool, width int, beep bool) []byte {
	builder := NewEscPosBuilder(width)

	if beep {
		builder.Beep()
	}

	builder.AlignCenter()
	if isReprint {
		builder.Bold(true).Line("*** [ พิมพ์ซ้ำ / REPRINT ] ***").Bold(false)
	}

	builder.Divider("=")

	// Header
	builder.AlignLeft()
	builder.Bold(true).LargeSize().Line(fmt.Sprintf("โต๊ะ: %s", order.TableNumber)).NormalSize().Bold(false)

	// Time format: HH:mm
	tStr := order.CreatedAt
	if parsedTime, err := time.Parse(time.RFC3339, order.CreatedAt); err == nil {
		tStr = parsedTime.Format("15:04:05 น.")
	} else if len(order.CreatedAt) >= 19 {
		tStr = order.CreatedAt[11:19]
	}

	builder.Line(fmt.Sprintf("เวลา: %s", tStr))

	// Short Order ID (first 8 chars)
	shortID := order.CloudOrderID
	if len(shortID) > 8 {
		shortID = shortID[:8]
	} else if shortID == "" {
		shortID = order.ID
		if len(shortID) > 8 {
			shortID = shortID[:8]
		}
	}
	builder.Line(fmt.Sprintf("ออเดอร์: #%s", shortID))

	builder.Divider("-")

	// Order Items
	builder.Bold(true)
	for _, item := range order.Items {
		builder.Line(fmt.Sprintf("%dx %s", item.Quantity, item.ProductName))
		if item.Note != "" {
			builder.Bold(false).Line(fmt.Sprintf("   หมายเหตุ: %s", item.Note)).Bold(true)
		}
		if item.Options != "" && item.Options != "{}" {
			builder.Bold(false).Line(fmt.Sprintf("   ตัวเลือก: %s", item.Options)).Bold(true)
		}
	}
	builder.Bold(false)

	builder.Divider("-")

	// Customer overall note
	note := order.CustomerNote
	if note == "" {
		note = "-"
	}
	builder.Line(fmt.Sprintf("หมายเหตุรวม: %s", note))

	builder.Divider("=")

	builder.Cut()
	return builder.Bytes()
}

// PrintKitchenOrder loads order from SQLite, sends to thermal printer, and updates print_status
func PrintKitchenOrder(db *sql.DB, cfg *PrinterConfig, orderID string, isReprint bool) error {
	if cfg == nil {
		cfg = LoadPrinterConfig()
	}

	if !cfg.Enabled {
		log.Printf("[Printer] Printer is disabled in config. Skipping print for order [%s]", orderID)
		return nil
	}

	// 1. Fetch Order from SQLite
	order, err := fetchOrderForPrint(db, orderID)
	if err != nil {
		log.Printf("❌ [Printer] Failed to load order [%s]: %v", orderID, err)
		updatePrintStatus(db, orderID, "failed")
		return err
	}

	// 2. Build ESC/POS Payload
	slipBytes := BuildKitchenSlip(order, isReprint, cfg.PaperWidth, cfg.BuzzerEnabled)

	// 3. Send to Network Thermal Printer
	err = SendRawToPrinter(cfg.PrinterIP, cfg.PrinterPort, slipBytes)
	if err != nil {
		log.Printf("❌ [Printer] Print failed for Order [%s] at %s:%d: %v",
			orderID, cfg.PrinterIP, cfg.PrinterPort, err)
		updatePrintStatus(db, orderID, "failed")
		return err
	}

	// 4. Update status to 'printed'
	updatePrintStatus(db, orderID, "printed")
	log.Printf("🖨️ [Printer] Kitchen slip printed successfully for Table %s (Order: %s, Reprint: %v)",
		order.TableNumber, orderID, isReprint)

	return nil
}

// ReprintKitchenOrder reprints a kitchen order with '[พิมพ์ซ้ำ]' mark
func ReprintKitchenOrder(db *sql.DB, cfg *PrinterConfig, orderID string) error {
	log.Printf("[Printer] 🔄 Requesting Reprint for Order [%s]", orderID)
	return PrintKitchenOrder(db, cfg, orderID, true)
}

func fetchOrderForPrint(db *sql.DB, orderID string) (*OrderForPrint, error) {
	query := `
		SELECT id, cloud_order_id, table_number, customer_note, created_at, print_status
		FROM qr_orders
		WHERE cloud_order_id = ? OR id = ?
		LIMIT 1
	`
	var o OrderForPrint
	var cloudID, note, created, status sql.NullString

	err := db.QueryRow(query, orderID, orderID).Scan(
		&o.ID, &cloudID, &o.TableNumber, &note, &created, &status,
	)
	if err != nil {
		return nil, err
	}

	if cloudID.Valid {
		o.CloudOrderID = cloudID.String
	}
	if note.Valid {
		o.CustomerNote = note.String
	}
	if created.Valid {
		o.CreatedAt = created.String
	}
	if status.Valid {
		o.PrintStatus = status.String
	}

	// Fetch Items
	itemsQuery := `
		SELECT product_name, quantity, note, options
		FROM qr_order_items
		WHERE order_id = ?
	`
	rows, err := db.Query(itemsQuery, o.ID)
	if err != nil {
		return &o, nil
	}
	defer rows.Close()

	for rows.Next() {
		var item ItemForPrint
		var itemNote, itemOpt sql.NullString
		if err := rows.Scan(&item.ProductName, &item.Quantity, &itemNote, &itemOpt); err == nil {
			if itemNote.Valid {
				item.Note = itemNote.String
			}
			if itemOpt.Valid {
				item.Options = itemOpt.String
			}
			o.Items = append(o.Items, item)
		}
	}

	return &o, nil
}

func updatePrintStatus(db *sql.DB, orderID string, status string) {
	nowStr := time.Now().Format(time.RFC3339)
	var query string
	if status == "printed" {
		query = "UPDATE qr_orders SET print_status = ?, printed_at = ?, updated_at = ? WHERE cloud_order_id = ? OR id = ?"
		_, _ = db.Exec(query, status, nowStr, nowStr, orderID, orderID)
	} else {
		query = "UPDATE qr_orders SET print_status = ?, updated_at = ? WHERE cloud_order_id = ? OR id = ?"
		_, _ = db.Exec(query, status, nowStr, orderID, orderID)
	}
}
