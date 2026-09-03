# SunPOS API Conventions

## 1. REST API Standard Structures

### Standard Authenticated Response
Used for Backoffice, POS operations, and administrative APIs:

```json
{
  "success": true,
  "data": { ... },
  "message": "Operation completed successfully",
  "timestamp": "2026-09-03T10:00:00Z"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "INVALID_ACTIVE_KEY",
    "message": "Branch Active Key verification failed",
    "details": []
  },
  "timestamp": "2026-09-03T10:00:00Z"
}
```

---

## 2. Public QR Ordering API (Zero-Authentication)

Customers scan physical QR codes at dining tables and access these public endpoints without requiring credentials:

### `GET /api/public/menu/{branchId}`
Returns the active menu layout, categories, and products for a branch.
```json
{
  "branchId": "branch-001",
  "branchName": "Sun Shabu & Grill Premium",
  "categories": [
    {
      "id": "cat-meat",
      "name": "เนื้อและชาบูพรีเมียม",
      "sortOrder": 1,
      "products": [
        {
          "id": "P001",
          "categoryId": "cat-meat",
          "name": "เนื้อวากิว A5 สไลซ์",
          "description": "เนื้อวากิวนุ่มพิเศษ ลายหินอ่อน",
          "price": 580.00,
          "imageUrl": "https://...",
          "isAvailable": true
        }
      ]
    }
  ]
}
```

### `POST /api/public/orders`
Submits a customer order. Supports header `Idempotency-Key` to block duplicate charges.
- **Request Headers**:
  - `Content-Type: application/json`
  - `Idempotency-Key: <UUID>` *(Recommended)*
- **Request Body**:
```json
{
  "branchId": "branch-001",
  "tableNumber": "A05",
  "customerNote": "ไม่ใส่ผักชี",
  "items": [
    {
      "productId": "P001",
      "productName": "เนื้อวากิว A5 สไลซ์",
      "quantity": 2,
      "unitPrice": 580.00,
      "options": { "level": "spicy" },
      "note": "ขอสุกปานกลาง"
    }
  ]
}
```
- **Response (`200 OK`)**:
```json
{
  "orderId": "3ccca4a8-1a04-4348-832d-2e50a218b914",
  "status": "pending",
  "message": "Order created successfully"
}
```

---

## 3. WebSocket STOMP & Branch Communication

- **STOMP Endpoint**: `/ws`
- **Transport**: Raw WebSocket (and SockJS fallback)
- **Handshake / Connect Headers**:
  - `branchId: <BRANCH_ID>`
  - `activeKey: <BRANCH_ACTIVE_KEY>`
- **Broker Channels**:
  - Subscription: `/topic/branch/{branchId}/orders` (Order dispatch push from cloud)
  - Broadcast: `/topic/branch/{branchId}/broadcast` (System announcements / menu invalidation)

---

## 4. Internal Branch Synchronization & Monitoring APIs

### `POST /api/internal/orders/{orderId}/ack`
Called by the branch Go service after successfully committing an order to local SQLite:
- **Headers**: `branchId: <ID>`, `activeKey: <KEY>`
- **Response**:
```json
{
  "orderId": "3ccca4a8-1a04-4348-832d-2e50a218b914",
  "status": "received",
  "message": "Order acknowledged successfully"
}
```

### `GET /api/internal/branches/{branchId}/status`
Real-time operational health check for a branch:
```json
{
  "branchId": "branch-001",
  "isOnline": true,
  "connectedAt": "2026-09-03T10:15:30Z",
  "pendingOrdersCount": 0
}
```

### `GET /api/internal/branches/status`
Lists all active branch sessions and current connection timestamps.

---

## 5. Local Branch Go Service REST Endpoints (Port 8888)

- `GET /api/health`: Health check and uptime status.
- `POST /api/orders/qr/{orderId}/reprint`: Trigger immediate kitchen thermal reprint with reprint watermark.
- `GET /api/device/identity`: Retrieve local POS device activation info.
