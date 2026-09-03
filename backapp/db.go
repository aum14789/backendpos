package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite"
)

var DB *sql.DB

func InitDB(dbPath string) (*sql.DB, error) {
	if dbPath == "" {
		dbPath = "pos.db"
	}

	dir := filepath.Dir(dbPath)
	if dir != "" && dir != "." {
		_ = os.MkdirAll(dir, 0755)
	}

	var err error
	DB, err = sql.Open("sqlite", dbPath+"?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)")
	if err != nil {
		return nil, fmt.Errorf("failed to open sqlite database: %w", err)
	}

	if err = DB.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping sqlite database: %w", err)
	}

	if err := migrateSchema(DB); err != nil {
		return nil, fmt.Errorf("failed to migrate database schema: %w", err)
	}

	log.Printf("[DB] SQLite database initialized successfully at %s", dbPath)
	return DB, nil
}

func migrateSchema(db *sql.DB) error {
	schema := `
	CREATE TABLE IF NOT EXISTS cached_branches (
		branch_id TEXT PRIMARY KEY,
		company_id TEXT NOT NULL,
		name TEXT NOT NULL,
		code TEXT NOT NULL,
		business_day_close_time TEXT DEFAULT '02:00',
		tax_rate REAL DEFAULT 7.0,
		service_charge_rate REAL DEFAULT 0.0
	);

	CREATE TABLE IF NOT EXISTS cached_devices (
		device_id TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		device_name TEXT NOT NULL,
		device_code TEXT NOT NULL,
		device_type TEXT NOT NULL
	);

	CREATE TABLE IF NOT EXISTS cached_users (
		user_id TEXT PRIMARY KEY,
		company_id TEXT NOT NULL,
		username TEXT NOT NULL,
		full_name TEXT NOT NULL,
		pin_hash TEXT,
		is_active INTEGER DEFAULT 1
	);

	CREATE TABLE IF NOT EXISTS cached_permissions (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		user_id TEXT NOT NULL,
		permission_code TEXT NOT NULL
	);

	CREATE TABLE IF NOT EXISTS room_zones (
		zone_id TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		name TEXT NOT NULL,
		zone_type TEXT DEFAULT 'DINE_IN',
		sort_order INTEGER DEFAULT 0,
		is_active INTEGER DEFAULT 1
	);

	CREATE TABLE IF NOT EXISTS room_tables (
		table_id TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		zone_id TEXT,
		table_type_id TEXT,
		name_number TEXT NOT NULL,
		capacity INTEGER DEFAULT 4,
		status TEXT DEFAULT 'AVAILABLE',
		is_active INTEGER DEFAULT 1
	);

	CREATE TABLE IF NOT EXISTS room_table_sessions (
		session_id TEXT PRIMARY KEY,
		table_id TEXT NOT NULL,
		branch_id TEXT NOT NULL,
		opened_at INTEGER NOT NULL,
		closed_at INTEGER,
		status TEXT DEFAULT 'ACTIVE',
		opened_by TEXT,
		closed_by TEXT
	);

	CREATE TABLE IF NOT EXISTS room_menu_categories (
		category_id TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		name TEXT NOT NULL,
		description TEXT,
		sort_order INTEGER DEFAULT 0,
		is_active INTEGER DEFAULT 1
	);

	CREATE TABLE IF NOT EXISTS room_menu_items (
		item_id TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		category_id TEXT NOT NULL,
		name TEXT NOT NULL,
		description TEXT,
		sku TEXT,
		base_price INTEGER DEFAULT 0,
		availability TEXT DEFAULT 'AVAILABLE',
		image_url TEXT,
		sort_order INTEGER DEFAULT 0,
		is_active INTEGER DEFAULT 1,
		allow_decimal INTEGER DEFAULT 0,
		unit_name TEXT DEFAULT ''
	);

	CREATE TABLE IF NOT EXISTS room_buffet_tiers (
		tier_id TEXT PRIMARY KEY,
		promotion_id TEXT NOT NULL,
		name TEXT NOT NULL,
		adult_price INTEGER DEFAULT 0,
		child_price INTEGER DEFAULT 0,
		time_limit_minutes INTEGER DEFAULT 90,
		brand_id TEXT,
		branch_id TEXT,
		is_active INTEGER DEFAULT 1
	);

	CREATE TABLE IF NOT EXISTS room_buffet_tier_menu_items (
		buffet_tier_id TEXT NOT NULL,
		menu_item_id TEXT NOT NULL,
		PRIMARY KEY (buffet_tier_id, menu_item_id)
	);

	CREATE TABLE IF NOT EXISTS room_buffet_sessions (
		session_id TEXT PRIMARY KEY,
		order_id TEXT NOT NULL,
		branch_id TEXT NOT NULL,
		buffet_tier_id TEXT NOT NULL,
		adult_count INTEGER DEFAULT 1,
		child_count INTEGER DEFAULT 0,
		adult_price_snapshot INTEGER NOT NULL,
		child_price_snapshot INTEGER NOT NULL,
		time_limit_minutes INTEGER DEFAULT 90,
		started_at INTEGER NOT NULL,
		expires_at INTEGER NOT NULL,
		closed_at INTEGER,
		status TEXT DEFAULT 'ACTIVE',
		created_by TEXT
	);

	CREATE TABLE IF NOT EXISTS room_customers (
		customer_id TEXT PRIMARY KEY,
		display_name TEXT NOT NULL,
		phone TEXT NOT NULL,
		member_id TEXT,
		line_id TEXT,
		email TEXT,
		tier_code TEXT DEFAULT 'SILVER',
		tier_name TEXT DEFAULT 'สมาชิกทั่วไป (Silver)',
		discount_percent REAL DEFAULT 0.0,
		points_balance REAL DEFAULT 0.0,
		customer_group TEXT DEFAULT 'GENERAL',
		created_at INTEGER NOT NULL
	);

	CREATE TABLE IF NOT EXISTS room_promotions (
		promotion_id TEXT PRIMARY KEY,
		code TEXT NOT NULL,
		name TEXT NOT NULL,
		description TEXT,
		promo_type TEXT NOT NULL,
		priority INTEGER DEFAULT 0,
		is_active INTEGER DEFAULT 1,
		discount_rate REAL DEFAULT 0.0,
		discount_amount INTEGER DEFAULT 0,
		min_order_amount INTEGER DEFAULT 0,
		stacking_policy TEXT DEFAULT 'STACKABLE'
	);

	CREATE TABLE IF NOT EXISTS room_orders (
		order_id TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		customer_id TEXT,
		table_id TEXT,
		table_session_id TEXT,
		order_number TEXT NOT NULL,
		order_type TEXT DEFAULT 'DINE_IN',
		channel TEXT DEFAULT 'POS',
		status TEXT DEFAULT 'OPEN',
		kitchen_status TEXT DEFAULT 'NOT_SENT',
		buffet_session_id TEXT,
		subtotal_amount INTEGER DEFAULT 0,
		discount_amount INTEGER DEFAULT 0,
		service_charge_amount INTEGER DEFAULT 0,
		tax_amount INTEGER DEFAULT 0,
		total_amount INTEGER DEFAULT 0,
		cloud_order_id TEXT,
		created_by TEXT,
		created_at INTEGER NOT NULL
	);
	CREATE UNIQUE INDEX IF NOT EXISTS idx_room_orders_cloud_order_id ON room_orders (cloud_order_id);

	CREATE TABLE IF NOT EXISTS room_order_items (
		order_item_id TEXT PRIMARY KEY,
		order_id TEXT NOT NULL,
		menu_item_id TEXT NOT NULL,
		name_snapshot TEXT NOT NULL,
		unit_price_snapshot INTEGER NOT NULL,
		quantity INTEGER DEFAULT 1,
		notes TEXT,
		subtotal INTEGER DEFAULT 0,
		kitchen_status TEXT DEFAULT 'NOT_SENT',
		is_buffet_included INTEGER DEFAULT 0,
		selected_modifiers TEXT
	);

	CREATE TABLE IF NOT EXISTS room_payment_transactions (
		payment_id TEXT PRIMARY KEY,
		order_id TEXT NOT NULL,
		branch_id TEXT NOT NULL,
		device_id TEXT,
		shift_id TEXT,
		payment_method TEXT DEFAULT 'CASH',
		amount INTEGER DEFAULT 0,
		tendered_amount INTEGER DEFAULT 0,
		change_amount INTEGER DEFAULT 0,
		status TEXT DEFAULT 'SUCCESS',
		created_by TEXT,
		created_at INTEGER NOT NULL
	);

	CREATE TABLE IF NOT EXISTS room_tax_invoices (
		invoice_id TEXT PRIMARY KEY,
		order_id TEXT NOT NULL,
		taxpayer_name TEXT NOT NULL,
		tax_id TEXT NOT NULL,
		branch_number TEXT NOT NULL,
		address TEXT NOT NULL,
		phone TEXT,
		created_at INTEGER NOT NULL
	);

	CREATE TABLE IF NOT EXISTS sync_queue (
		queue_id TEXT PRIMARY KEY,
		entity_type TEXT NOT NULL,
		entity_id TEXT NOT NULL,
		action TEXT NOT NULL,
		payload_json TEXT NOT NULL,
		status TEXT DEFAULT 'PENDING',
		retry_count INTEGER DEFAULT 0,
		error_message TEXT,
		created_at INTEGER NOT NULL,
		synced_at INTEGER
	);
	CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue (status, created_at);

	CREATE TABLE IF NOT EXISTS sync_metadata (
		key TEXT PRIMARY KEY,
		value TEXT NOT NULL,
		updated_at INTEGER NOT NULL
	);

	CREATE TABLE IF NOT EXISTS device_identity (
		id INTEGER PRIMARY KEY CHECK (id = 1),
		device_id TEXT NOT NULL,
		branch_id TEXT NOT NULL,
		branch_name TEXT NOT NULL,
		branch_code TEXT NOT NULL,
		company_id TEXT NOT NULL,
		company_name TEXT NOT NULL,
		device_name TEXT NOT NULL,
		device_code TEXT NOT NULL,
		activated_at INTEGER NOT NULL,
		activation_code TEXT NOT NULL,
		last_verified_at INTEGER,
		cloud_api_url TEXT DEFAULT ''
	);

	CREATE TABLE IF NOT EXISTS activation_codes (
		code TEXT PRIMARY KEY,
		branch_id TEXT NOT NULL,
		branch_name TEXT NOT NULL,
		branch_code TEXT NOT NULL,
		device_code TEXT NOT NULL,
		device_name TEXT NOT NULL,
		created_at INTEGER NOT NULL,
		expires_at INTEGER NOT NULL,
		status TEXT DEFAULT 'UNUSED',
		activated_at INTEGER,
		activated_device_id TEXT
	);
	CREATE INDEX IF NOT EXISTS idx_activation_codes_status ON activation_codes (status, expires_at);

	CREATE TABLE IF NOT EXISTS qr_orders (
		id TEXT PRIMARY KEY,
		cloud_order_id TEXT UNIQUE,
		branch_id TEXT NOT NULL,
		table_number TEXT NOT NULL,
		status TEXT NOT NULL DEFAULT 'pending',
		customer_note TEXT,
		total_amount REAL NOT NULL DEFAULT 0.0,
		source TEXT NOT NULL DEFAULT 'qr',
		pos_order_id TEXT,
		print_status TEXT NOT NULL DEFAULT 'pending',
		printed_at TEXT,
		synced_at TEXT,
		created_at TEXT NOT NULL,
		updated_at TEXT NOT NULL
	);
	CREATE UNIQUE INDEX IF NOT EXISTS idx_qr_orders_cloud_id ON qr_orders (cloud_order_id);
	CREATE INDEX IF NOT EXISTS idx_qr_orders_branch_status ON qr_orders (branch_id, status);

	CREATE TABLE IF NOT EXISTS qr_order_items (
		id TEXT PRIMARY KEY,
		order_id TEXT NOT NULL REFERENCES qr_orders(id) ON DELETE CASCADE,
		product_id TEXT NOT NULL,
		product_name TEXT NOT NULL,
		quantity INTEGER NOT NULL DEFAULT 1,
		unit_price REAL NOT NULL DEFAULT 0.0,
		options TEXT,
		note TEXT
	);
	CREATE INDEX IF NOT EXISTS idx_qr_order_items_order ON qr_order_items (order_id);
	`
	if _, err := db.Exec(schema); err != nil {
		return err
	}

	// Safe alter column migrations for existing databases
	_, _ = db.Exec("ALTER TABLE room_menu_items ADD COLUMN allow_decimal INTEGER DEFAULT 0")
	_, _ = db.Exec("ALTER TABLE room_menu_items ADD COLUMN unit_name TEXT DEFAULT ''")
	_, _ = db.Exec("ALTER TABLE room_orders ADD COLUMN cloud_order_id TEXT")
	_, _ = db.Exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_room_orders_cloud_order_id ON room_orders (cloud_order_id)")
	_, _ = db.Exec("ALTER TABLE qr_orders ADD COLUMN pos_order_id TEXT")

	// Seed standard buffet tiers if table is empty
	var tierCount int
	if err := db.QueryRow("SELECT COUNT(*) FROM room_buffet_tiers").Scan(&tierCount); err == nil && tierCount == 0 {
		_, _ = db.Exec(`
			INSERT OR IGNORE INTO room_buffet_tiers (tier_id, promotion_id, name, adult_price, child_price, time_limit_minutes, brand_id, branch_id, is_active)
			VALUES 
			('tier-std-399', 'promo-buf-std', 'Standard Buffet ฿399', 39900, 19900, 90, 'brand-001', 'branch-001', 1),
			('tier-prem-599', 'promo-buf-prem', 'Premium Wagyu Buffet ฿599', 59900, 29900, 120, 'brand-001', 'branch-001', 1),
			('tier-yaki-699', 'promo-buf-yaki-699', 'Yakiniku Classic Buffet ฿699', 69900, 34900, 100, 'brand-002', 'branch-003', 1),
			('tier-yaki-999', 'promo-buf-yaki-999', 'Ultimate Wagyu & Seafood ฿999', 99900, 49900, 120, 'brand-002', 'branch-003', 1)
		`)
	}

	return nil
}

// GetDeviceIdentity loads current POS device identity from SQLite
func GetDeviceIdentity(db *sql.DB) (*DeviceIdentity, error) {
	if db == nil {
		return nil, fmt.Errorf("database not initialized")
	}

	row := db.QueryRow(`
		SELECT device_id, branch_id, branch_name, branch_code, company_id, company_name, 
		       device_name, device_code, activated_at, activation_code, last_verified_at, cloud_api_url
		FROM device_identity WHERE id = 1
	`)

	var ident DeviceIdentity
	var lastVerified sql.NullInt64
	var cloudUrl sql.NullString

	err := row.Scan(
		&ident.DeviceID, &ident.BranchID, &ident.BranchName, &ident.BranchCode,
		&ident.CompanyID, &ident.CompanyName, &ident.DeviceName, &ident.DeviceCode,
		&ident.ActivatedAt, &ident.ActivationCode, &lastVerified, &cloudUrl,
	)

	if err == sql.ErrNoRows {
		return nil, nil // Not activated yet
	}
	if err != nil {
		return nil, err
	}

	if lastVerified.Valid {
		ident.LastVerifiedAt = &lastVerified.Int64
	}
	if cloudUrl.Valid {
		ident.CloudApiUrl = cloudUrl.String
	}

	return &ident, nil
}

// SaveDeviceIdentity inserts or replaces the single device identity in SQLite
func SaveDeviceIdentity(db *sql.DB, ident DeviceIdentity) error {
	if db == nil {
		return fmt.Errorf("database not initialized")
	}

	query := `
		INSERT OR REPLACE INTO device_identity (
			id, device_id, branch_id, branch_name, branch_code, company_id, company_name,
			device_name, device_code, activated_at, activation_code, last_verified_at, cloud_api_url
		) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`

	_, err := db.Exec(query,
		ident.DeviceID, ident.BranchID, ident.BranchName, ident.BranchCode,
		ident.CompanyID, ident.CompanyName, ident.DeviceName, ident.DeviceCode,
		ident.ActivatedAt, ident.ActivationCode, ident.LastVerifiedAt, ident.CloudApiUrl,
	)
	return err
}

// ClearDeviceIdentity removes device identity from SQLite (Deactivation)
func ClearDeviceIdentity(db *sql.DB) error {
	if db == nil {
		return fmt.Errorf("database not initialized")
	}
	_, err := db.Exec("DELETE FROM device_identity WHERE id = 1")
	return err
}

