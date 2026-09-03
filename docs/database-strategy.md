# SunPOS Database Strategy

## 1. Multi-Database Architecture

- **Cloud Central Database**: Neon PostgreSQL (Serverless / Connection Pooler on AWS ap-southeast-1)
  - Engine: PostgreSQL 16+
  - Connection Pooler: PgBouncer enabled on port 5432 (`sslmode=require&channel_binding=require`)
  - Target Schema Migration: Flyway Version 32+
- **Branch Local Database**: SQLite 3 (Pure Go `modernc.org/sqlite` in `backapp`)
  - Journal Mode: **WAL (Write-Ahead Logging)** with `busy_timeout=5000`
- **Mobile POS Local Database**: SQLite managed via Android Room 2.6+

## 2. Entity Identification & Primary Keys

- **Globally Unique IDs**: All domain entities and transaction records must use UUID (v4) strings or standard ULIDs.
- **Rationale**: Guarantees zero ID collision when orders, shifts, or stock movements are created concurrently on offline branch devices or customer mobile browsers before synchronizing to the cloud.

## 3. QR Ordering Schema (Neon PostgreSQL & Local SQLite)

### `qr_orders` (Parent Order Entity)
- `id` (VARCHAR(36) PK): UUID representing the order.
- `cloud_order_id` (VARCHAR(36) UNIQUE on SQLite): Links local branch record to Cloud order ID.
- `branch_id` (VARCHAR(36) NOT NULL): Associated restaurant branch.
- `table_number` (VARCHAR(50) NOT NULL): Dining table identifier (e.g. "A05", "VIP-1").
- `status` (VARCHAR(30) NOT NULL): State lifecycle (`pending`, `sent_to_branch`, `received`, `preparing`, `ready`, `completed`, `cancelled`).
- `customer_note` (TEXT): Free-form special instructions from customer to kitchen.
- `total_amount` (DECIMAL(12, 2) / REAL): Total order price.
- `source` (VARCHAR(20) NOT NULL DEFAULT 'qr'): Order ingestion channel.
- `idempotency_key` (VARCHAR(100)): Unique client token to reject duplicated submissions.
- `print_status` (VARCHAR(20) NOT NULL DEFAULT 'pending'): Kitchen printing status (`pending`, `printed`, `failed`).
- `printed_at` (TIMESTAMP WITH TIME ZONE): Timestamp when kitchen slip was physically printed.
- `synced_at` (TIMESTAMP WITH TIME ZONE): Reconciliation timestamp.
- `created_at` / `updated_at`: Standard ISO 8601 audit timestamps.

### `qr_order_items` (Child Order Items)
- `id` (VARCHAR(36) PK): UUID for line item.
- `order_id` (VARCHAR(36) NOT NULL REFERENCES qr_orders(id) ON DELETE CASCADE).
- `product_id` (VARCHAR(36) NOT NULL): SKU / Product ID.
- `product_name` (VARCHAR(255) NOT NULL): Snapshot of product name at ordering time.
- `quantity` (INT NOT NULL DEFAULT 1): Ordered quantity.
- `unit_price` (DECIMAL(12, 2) NOT NULL): Price per unit snapshot.
- `options` (TEXT / JSONB): Customization payload (e.g. spicy level, add-ons).
- `note` (TEXT): Line-item specific preparation note (e.g. "ไม่ใส่ผักชี").

### Indexing Strategy
- Cloud Composite Index: `idx_qr_orders_branch_status_created` on `(branch_id, status, created_at DESC)`
- Cloud Composite Index: `idx_qr_orders_table_active` on `(branch_id, table_number, status)`
- Cloud Unique Partial Index: `uk_qr_orders_idempotency_key` on `(idempotency_key) WHERE idempotency_key IS NOT NULL`
- Local SQLite Unique Index: `idx_qr_orders_cloud_id` on `(cloud_order_id)`

## 4. Monetary & Quantity Representation

- **Monetary Values**:
  - PostgreSQL: `NUMERIC(15, 4)` mapped to Java/Kotlin `BigDecimal`.
  - SQLite: `REAL` / `INTEGER` (cents/satang) depending on local aggregate requirement.
  - Floating point types (`FLOAT`, `DOUBLE`) are strictly forbidden for financial calculations.
- **Quantities**:
  - Represented as `DECIMAL(12, 4)` to support weight and fractional unit measurements (e.g. 0.350 kg, 1.5 L).

## 5. Migration Strategy

- **Cloud Migration (Flyway)**:
  - Migrations reside in `backend/src/main/resources/db/migration/`.
  - Format: `V{VERSION}__{DESCRIPTION}.sql`.
  - Version History Highlights:
    - `V1`–`V30`: Core SunPOS domains (Organization, Catalog, Order, Inventory, Payment, Shift).
    - `V31`: QR Ordering baseline schema (`qr_orders`, `qr_order_items`).
    - `V32`: Added `idempotency_key` column and unique partial index to `qr_orders`.
  - **Rule**: Deployed Flyway scripts are immutable. Schema changes require a new versioned script.
- **Branch SQLite Migration**:
  - Automatically executed on startup inside `db.go` (`migrateSchema`) using idempotent DDL (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).
