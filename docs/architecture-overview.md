# SunPOS Architecture Overview

SunPOS is an enterprise-grade, multi-branch, multi-device restaurant management platform built with a **Cloud-First, Offline-Tolerant** architecture.

## High-Level System Architecture

```
+-----------------------------------------------------------------------------------------+
|                                    CUSTOMER LAYER                                       |
|                                                                                         |
|       [ Customer Mobile Browser (QR Ordering) ]                                         |
|       - React 19 + TypeScript + Vite + TailwindCSS v4                                   |
|       - Zustand Session Store + React Router (Mobile-First)                             |
|       - Idempotent Order Dispatching                                                    |
+-----------------------------------------------------------------------------------------+
                                             |
                                  HTTPS / REST (Public)
                                             |
+-----------------------------------------------------------------------------------------+
|                       CLOUD BACKEND (Kotlin 2.1 + Spring Boot 3.x)                      |
|                            [ Modular Monolith Architecture ]                            |
|                                                                                         |
|  +-----------------------+  +-----------------------+  +-----------------------------+  |
|  | Organization & Branch |  | Identity & RBAC       |  | Catalog, Menu & Pricing     |  |
|  +-----------------------+  +-----------------------+  +-----------------------------+  |
|  | Order & Table Session |  | Payment & Shift       |  | Inventory, Recipe & WAC     |  |
|  +-----------------------+  +-----------------------+  +-----------------------------+  |
|  | QR Ordering Engine    |  | WebSocket Broker      |  | Branch Monitoring & ACK     |  |
|  | (Idempotency Guard)   |  | (STOMP + Active Key)  |  | (Real-time Online/Offline)  |  |
|  +-----------------------+  +-----------------------+  +-----------------------------+  |
+-----------------------------------------------------------------------------------------+
             |                                              |
      SQL Pool (HikariCP)                        Outbound STOMP over WS (TLS)
             v                                              v
+-----------------------------+         +-------------------------------------------------+
|   Neon PostgreSQL (Cloud)   |         |              BRANCH LOCAL ENGINE                |
|  - Serverless PostgreSQL    |         |                                                 |
|  - Flyway Migrations (V1-32)|         |   [ Go Branch Service (backapp) ]               |
|  - Active Key Store         |         |   - System Tray & Crash Supervisor (Go 1.27)    |
+-----------------------------+         |   - STOMP Client (Active Key Handshake)         |
                                        |   - SQLite WAL (pos.db with ACID Transactions)  |
                                        |   - Fallback Background Poller (30s Interval)   |
                                        |   - ESC/POS Thermal Network Printer (Raw TCP)   |
                                        |   - REST API (Port 8888)                        |
                                        +-------------------------------------------------+
                                                   |                    |
                                            Local HTTP / LAN      Raw TCP Port 9100
                                                   |                    |
                                        +--------------------+  +--------------------+
                                        |    Web / Android   |  | Kitchen Thermal    |
                                        |     POS Clients    |  | Printers (ESC/POS) |
                                        |  (sunpos / android)|  | (Slip Printing)    |
                                        +--------------------+  +--------------------+
```

## Core Architectural Principles

1. **Cloud-First with Resilient Local Branch Autonomy**:
   - Central database lives on **Neon PostgreSQL**, acting as the authoritative single source of truth for all branches, master catalog data, and financial reporting.
   - Each branch operates a local autonomous Go service (`backapp`) and/or Android POS client with local SQLite/Room storage, allowing normal billing and kitchen processing even during prolonged cloud network outages.

2. **Outbound Real-Time Communication (No Inbound Firewall Holes)**:
   - Branch machines establish **outbound-only** WebSocket connections to the Cloud STOMP broker.
   - No public static IP, DynDNS, or router port-forwarding is required at physical restaurant branches.
   - Mutual branch verification is handled through **Active Keys** (`branchId` + `activeKey`) during the STOMP connection handshake.

3. **Event-Driven Push with At-Least-Once Delivery & Idempotency**:
   - New QR orders arriving at the cloud are pushed instantly via WebSocket to the branch's dedicated topic `/topic/branch/{branchId}/orders`.
   - The branch acknowledges order storage via `POST /api/internal/orders/{orderId}/ack`.
   - A safety-net Fallback Poller runs every 30 seconds to reconcile any orders missed during reconnection windows.
   - Customers' orders are protected against duplicate submission using unique `Idempotency-Key` tokens.

4. **Modular Monolith Backend**:
   - Backend logic is encapsulated in bounded contexts: `organization`, `identity`, `catalog`, `order`, `qrorder`, `websocket`, `pricing`, `payment`, `shift`, `inventory`, `crm`, `sync`, and `audit`.
   - Communication between domains occurs via clean service interfaces, maintaining zero circular dependencies and preserving future decomposition paths into microservices if scaling demands.

5. **Financial Arithmetic & Auditing Rigor**:
   - Currency computations strictly utilize `BigDecimal` / `NUMERIC(15, 4)`. Floating point representation (`FLOAT`/`DOUBLE`) is forbidden for financial calculations.
   - Master data changes (e.g. price updates) never alter historically closed bills; transaction snapshots are preserved immutably.
