# SunPOS Domain Overview

The SunPOS platform is structured around clearly bounded contexts to maintain strong separation of concerns, high cohesion, and business clarity.

---

## Core Business Domains

### 1. Organization & Security
- **Company & Branch**: Multi-company tenancy and physical/virtual branch configuration with branch-level overrides (tax, service charge, business day close time).
- **Device & Active Key System**: Registration and activation of POS devices, kitchen printers, and background services using human-readable Active Keys (e.g. `SUN-XXXX-YYYY`).
- **User / Role / Permission**: Granular RBAC enforcing permissions (`ORDER_CREATE`, `PAYMENT_REFUND`, `DISCOUNT_OVERRIDE`, `STOCK_TRANSFER`).

### 2. Catalog & Menu
- **Catalog & Product**: Master item SKU definitions, base prices, categories, and tax applicability.
- **Menu Layout**: Branch-specific menu layouts, item availability schedules, and category display ordering.
- **Modifier & Option**: Product options (e.g. spicy levels, noodle types, extra toppings, mandatory selections).

### 3. Table & Floor Management
- **Zone & Table**: Physical floor layouts (Dine-in zones, Outdoor terrace, VIP rooms).
- **Table Session**: Active customer sessions, party size, adult/child split, and table lock state.

### 4. Order & Transaction
- **Order & OrderItem**: Central transaction entity supporting Dine-in, À la carte, Buffet, Takeaway, and Delivery.
- **Order Operations**: Split bill, merge bill, move order, item cancellation, void, and refund.
- **Status Lifecycle**: `DRAFT` $\rightarrow$ `SUBMITTED` $\rightarrow$ `IN_PREPARATION` $\rightarrow$ `SERVED` $\rightarrow$ `BILLED` $\rightarrow$ `PAID` $\rightarrow$ `CLOSED`.

### 5. QR Ordering & Real-Time Kitchen Dispatch
- **Public Ingestion**: Zero-authentication public endpoint (`POST /api/public/orders`) enabling mobile ordering for seated customers.
- **Idempotency Engine**: Cryptographic or UUID-based header deduplication preventing accidental multi-clicks or network-induced duplicated orders.
- **STOMP Dispatcher**: Real-time push broker broadcasting incoming orders to physical branch stations over WebSocket topics.
- **Kitchen Slip Printing Engine**: Autonomous conversion to ESC/POS binary streams with printer status tracking (`pending`, `printed`, `failed`) and kitchen reprint capabilities.

### 6. Pricing & Promotion Engine
- **Pricing Service**: Isolated engine computing Base Price, Automatic/Manual Promotions, Member Tier Discounts, Coupons, Service Charge, and Tax.
- **Promotion Engine**: Priority-based rules supporting Percentage, Fixed Discount, Buy X Get Y, Combo, Set Price, and Customer Tier discounts.
- **Pricing Snapshot**: Persists the complete calculation breakdown on every completed order.

### 7. Payment & Shift Management
- **Payment**: Multi-payment support (Cash, Credit/Debit Card, PromptPay QR, E-Wallet, Voucher) with partial payment handling.
- **Shift Management**: Cashier shift workflow (`OPEN_SHIFT` $\rightarrow$ `SALE` $\rightarrow$ `CASH_IN/OUT` $\rightarrow$ `CLOSE_SHIFT` $\rightarrow$ `EXPECTED_VS_ACTUAL_COUNT`).
- **Business Day**: Configurable daily closing time (e.g. 02:00 AM) defining a business day independently of calendar dates.

### 8. Inventory & Warehouse
- **Stock Movement**: Source-of-truth ledger for inventory changes (Purchase Receive, Sale Consumption, Transfer, Waste, Production, Adjustment).
- **Weighted Average Cost (WAC)**: Historical valuation tracking for purchased ingredients, finished goods, and central kitchen outputs.
- **Warehouse & Transfer**: Multi-warehouse tracking supporting transfer workflows (`REQUESTED` $\rightarrow$ `APPROVED` $\rightarrow$ `PICKING` $\rightarrow$ `SHIPPED` $\rightarrow$ `RECEIVED`).

### 9. Recipe, BOM & Production
- **Recipe**: Menu item ingredient ratios, yield percentages, and waste factors.
- **BOM (Bill of Materials)**: Central Kitchen bulk production formulas for semi-finished or finished goods.
- **Production Order**: Execution of bulk production converting raw materials to finished goods with calculated production costs.

### 10. Purchasing & Supplier
- **Supplier & PO**: Purchase Requests, Purchase Orders, Goods Receiving, and Returns to Suppliers.

### 11. Customer, Loyalty & CRM
- **Customer & Identity**: Linked phone, email, member ID, and LINE identity.
- **Loyalty & Points**: Ledger-based point earning, redemption, and tier upgrades/downgrades.
- **Coupon**: Idempotent single-use / multi-use vouchers with usage limits and restrictions.

### 12. Synchronization & Audit
- **Sync Engine**: Dual-channel engine handling both real-time WebSocket pushes and offline-first transactional outbox batches.
- **Audit Engine**: Immutable audit logs capturing user actions, financial overrides, voids, refunds, and master data edits.
