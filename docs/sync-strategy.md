# SunPOS Synchronization & Real-time Strategy

SunPOS employs a dual-channel synchronization architecture tailored for both **Asynchronous POS Outbox Sync** and **Low-Latency QR Order Dispatching**.

---

## 1. Dual-Channel Synchronization Architecture

```
                                  [ CUSTOMER LAYER ]
                                           |
                                  POST /api/public/orders
                                           v
                              +--------------------------+
                              |    CLOUD BACKEND ENGINE  |
                              |  (Spring Boot + STOMP)   |
                              +--------------------------+
                               /                        \
    (Channel A: Low-Latency Push)              (Channel B: Event Outbox)
               |                                           |
      STOMP over WebSocket                       HTTPS REST Sync Workers
               |                                           |
               v                                           v
  +--------------------------+                +--------------------------+
  |  Branch Go Listener      |                |  Android / Web POS       |
  |  (Live Order Push & ACK) |                |  (SyncWorker Outbox)     |
  +--------------------------+                +--------------------------+
               |                                           |
      SQLite local commit                         SQLite Room commit
```

---

## 2. Channel A: Real-Time QR Order Push & ACK Pipeline

### Architecture
- **Protocol**: STOMP 1.2 over WebSocket (TLS encrypted).
- **Handshake Authentication**: STOMP `CONNECT` frame intercepted by `ActiveKeyChannelInterceptor`, requiring valid `branchId` and `activeKey` validated against Neon PostgreSQL.
- **Push Mechanism**: When an order is created, `BranchOrderPushService` broadcasts to `/topic/branch/{branchId}/orders` if the branch session is active in `BranchSessionRegistry`.
- **Status Lifecycle**:
  1. Cloud creates order $\rightarrow$ Status: `pending`.
  2. Cloud detects branch online and dispatches via WebSocket $\rightarrow$ Status: `sent_to_branch`.
  3. Branch saves order to local SQLite inside an atomic transaction and executes HTTP `POST /api/internal/orders/{orderId}/ack` $\rightarrow$ Status: `received`.
  4. Local branch fires kitchen printer via raw TCP socket $\rightarrow$ Slip prints.

### Reconnection & Resilience
- **Exponential Backoff with Full Jitter**:
  When a WebSocket disconnects, the branch client pauses using:
  $$\text{Interval} = \min(30\text{s}, 2\text{s} \times 2^{\text{attempt}}) + \text{rand}(0, 1\text{s})$$
- **Fallback Poller (Safety Net)**:
  Every 30 seconds, a background ticker in `qr_order_listener.go` polls `GET /api/v1/qr/branch/{branchId}/pending`. Any unacknowledged orders missed during a disconnect are ingested, committed to SQLite, and acknowledged.

---

## 3. Channel B: POS Event-Driven Outbox Sync

For standard POS cashier terminals (Android POS / Web POS), direct database mirroring is prohibited due to schema variations, transaction boundaries, and network partition risks.

```
[ POS UI ] -> [ Domain Service ] -> [ Local DB Tables ] (Immediate Commit)
                                  -> [ sync_outbox Table ] (Atomic Event Enqueue)
                                              |
                                     [ SyncWorker Engine ]
                                              | (HTTPS POST /api/v1/sync/push)
                                              v
                                  [ Cloud Sync Controller ]
                                              | (Idempotent Apply)
                                              v
                                   [ Neon PostgreSQL DB ]
```

### Sync Outbox Schema
| Field | Type | Description |
|---|---|---|
| `event_id` | UUID | Unique ID of this sync event |
| `aggregate_type` | String | Domain name (`ORDER`, `PAYMENT`, `SHIFT`, `STOCK_MOVEMENT`) |
| `aggregate_id` | UUID | Target entity ID |
| `event_type` | String | Action verb (`ORDER_CREATED`, `PAYMENT_COMPLETED`) |
| `payload` | JSON | Complete snapshot payload of the event |
| `device_id` | UUID | Originating POS device ID |
| `branch_id` | UUID | Target branch ID |
| `created_at` | Long | Epoch millisecond timestamp |
| `status` | Enum | `PENDING`, `SYNCED`, `FAILED` |
| `retry_count` | Int | Incremented on retry attempts |
| `last_error` | String | Diagnostic message on sync failure |

---

## 4. Guarantees & Conflict Resolution

1. **At-Least-Once Delivery**:
   Both the QR ordering ACK cycle and the POS Outbox push guarantee delivery by retransmitting until an HTTP acknowledgment is received.
2. **Idempotent Application**:
   - Cloud deduplicates QR orders via the `Idempotency-Key` header and partial unique index `uk_qr_orders_idempotency_key`.
   - Branch SQLite deduplicates orders using `ON CONFLICT(cloud_order_id) DO UPDATE`.
   - Event Outbox deduplicates records using `event_id` lookup in `sync_events`.
3. **Master Data Authority (Cloud Single Source of Truth)**:
   Master data (Catalog, Products, Prices, Promos, Table Layouts) is strictly authored on Cloud. Branch terminals pull changes via `GET /api/v1/sync/pull` based on timestamp deltas.
