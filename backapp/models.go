package main

import "time"

// User represents authenticated POS operator
type User struct {
	UserID      string   `json:"userId"`
	CompanyID   string   `json:"companyId"`
	Username    string   `json:"username"`
	FullName    string   `json:"fullName"`
	PinHash     string   `json:"-"`
	IsActive    bool     `json:"isActive"`
	Permissions []string `json:"permissions"`
}

// Branch represents branch settings
type Branch struct {
	BranchID             string  `json:"branchId"`
	CompanyID            string  `json:"companyId"`
	Name                 string  `json:"name"`
	Code                 string  `json:"code"`
	BusinessDayCloseTime string  `json:"businessDayCloseTime"`
	TaxRate              float64 `json:"taxRate"`
	ServiceChargeRate    float64 `json:"serviceChargeRate"`
}

// Device represents POS terminal
type Device struct {
	DeviceID   string `json:"deviceId"`
	BranchID   string `json:"branchId"`
	DeviceName string `json:"deviceName"`
	DeviceCode string `json:"deviceCode"`
	DeviceType string `json:"deviceType"`
}

// Zone represents restaurant floor section
type Zone struct {
	ZoneID    string `json:"zoneId"`
	BranchID  string `json:"branchId"`
	Name      string `json:"name"`
	ZoneType  string `json:"zoneType"` // DINE_IN, BUFFET
	SortOrder int    `json:"sortOrder"`
	IsActive  bool   `json:"isActive"`
}

// Table represents restaurant table
type Table struct {
	TableID     string `json:"tableId"`
	BranchID    string `json:"branchId"`
	ZoneID      string `json:"zoneId"`
	TableTypeID string `json:"tableTypeId"`
	NameNumber  string `json:"nameNumber"`
	Capacity    int    `json:"capacity"`
	Status      string `json:"status"` // AVAILABLE, OCCUPIED, WAITING_FOOD, READY_TO_SERVE, WAITING_PAYMENT, RESERVED
	IsActive    bool   `json:"isActive"`
}

// MenuCategory represents menu grouping
type MenuCategory struct {
	CategoryID  string `json:"categoryId"`
	BranchID    string `json:"branchId"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	SortOrder   int    `json:"sortOrder"`
	IsActive    bool   `json:"isActive"`
}

// MenuItem represents individual menu dish or drink
type MenuItem struct {
	ItemID       string `json:"itemId"`
	BranchID     string `json:"branchId"`
	CategoryID   string `json:"categoryId"`
	Name         string `json:"name"`
	Description  string `json:"description,omitempty"`
	SKU          string `json:"sku,omitempty"`
	BasePrice    int64  `json:"basePrice"` // In Satang (1 Baht = 100 Satang)
	Availability string `json:"availability"` // AVAILABLE, SOLD_OUT, DISABLED
	ImageURL     string `json:"imageUrl,omitempty"`
	SortOrder    int    `json:"sortOrder"`
	IsActive     bool   `json:"isActive"`
	AllowDecimal bool   `json:"allowDecimal"`
	UnitName     string `json:"unitName,omitempty"`
}

// BuffetTier represents buffet package
type BuffetTier struct {
	TierID           string   `json:"tierId"`
	PromotionID      string   `json:"promotionId"`
	Name             string   `json:"name"`
	AdultPrice       int64    `json:"adultPrice"` // In Satang
	ChildPrice       int64    `json:"childPrice"` // In Satang
	TimeLimitMinutes int      `json:"timeLimitMinutes"`
	BrandID          string   `json:"brandId,omitempty"`
	BranchID         string   `json:"branchId,omitempty"`
	IsActive         bool     `json:"isActive"`
	EligibleItemIDs  []string `json:"eligibleItemIds,omitempty"`
}

// BuffetSession represents active buffet table timing
type BuffetSession struct {
	SessionID          string `json:"sessionId"`
	OrderID            string `json:"orderId"`
	BranchID           string `json:"branchId"`
	BuffetTierID       string `json:"buffetTierId"`
	AdultCount         int    `json:"adultCount"`
	ChildCount         int    `json:"childCount"`
	AdultPriceSnapshot int64  `json:"adultPriceSnapshot"`
	ChildPriceSnapshot int64  `json:"childPriceSnapshot"`
	TimeLimitMinutes   int    `json:"timeLimitMinutes"`
	StartedAt          int64  `json:"startedAt"` // Unix epoch ms
	ExpiresAt          int64  `json:"expiresAt"` // Unix epoch ms
	ClosedAt           *int64 `json:"closedAt,omitempty"`
	Status             string `json:"status"` // ACTIVE, TIME_WARNING, EXPIRED, CLOSED
	CreatedBy          string `json:"createdBy,omitempty"`
}

// Customer represents CRM member
type Customer struct {
	CustomerID         string  `json:"customerId"`
	DisplayName        string  `json:"displayName"`
	Phone              string  `json:"phone"`
	MemberID           string  `json:"memberId,omitempty"`
	LineID             string  `json:"lineId,omitempty"`
	Email              string  `json:"email,omitempty"`
	TierCode           string  `json:"tierCode"` // SILVER, GOLD, PLATINUM
	TierName           string  `json:"tierName"`
	DiscountPercentage float64 `json:"discountPercentage"`
	PointMultiplier    float64 `json:"pointMultiplier"`
	PointsBalance      float64 `json:"pointsBalance"`
	CustomerGroup      string  `json:"customerGroup"`
	CreatedAt          int64   `json:"createdAt"`
}

// Promotion represents automatic/coupon discount
type Promotion struct {
	PromotionID    string   `json:"promotionId"`
	Code           string   `json:"code"`
	Name           string   `json:"name"`
	Description    string   `json:"description,omitempty"`
	PromoType      string   `json:"promoType"` // PERCENTAGE, FIXED_AMOUNT, BUY_1_GET_1, SET_PRICE
	Priority       int      `json:"priority"`
	IsActive       bool     `json:"isActive"`
	DiscountRate   float64  `json:"discountRate"`
	DiscountAmount int64    `json:"discountAmount"` // Satang
	MinOrderAmount int64    `json:"minOrderAmount"` // Satang
	StackingPolicy string   `json:"stackingPolicy"` // STACKABLE, NON_STACKABLE
	EligibleItems  []string `json:"eligibleItems,omitempty"`
}

// Order represents transaction order
type Order struct {
	OrderID             string      `json:"orderId"`
	BranchID            string      `json:"branchId"`
	CustomerID          *string     `json:"customerId,omitempty"`
	TableID             *string     `json:"tableId,omitempty"`
	TableSessionID      *string     `json:"tableSessionId,omitempty"`
	OrderNumber         string      `json:"orderNumber"`
	OrderType           string      `json:"orderType"` // DINE_IN, BUFFET, TAKEAWAY, DELIVERY
	Channel             string      `json:"channel"`   // POS, QR, LINE, WEB
	Status              string      `json:"status"`    // OPEN, IN_KITCHEN, WAITING_PAYMENT, COMPLETED, CANCELLED, VOIDED
	KitchenStatus       string      `json:"kitchenStatus"`
	BuffetSessionID     *string     `json:"buffetSessionId,omitempty"`
	SubtotalAmount      int64       `json:"subtotalAmount"` // In Satang
	DiscountAmount      int64       `json:"discountAmount"`
	ServiceChargeAmount int64       `json:"serviceChargeAmount"`
	TaxAmount           int64       `json:"taxAmount"`
	TotalAmount         int64       `json:"totalAmount"` // Grand Total In Satang
	CreatedBy           *string     `json:"createdBy,omitempty"`
	CreatedAt           int64       `json:"createdAt"`
	Items               []OrderItem `json:"items,omitempty"`
}

// OrderItem represents individual line item in order
type OrderItem struct {
	OrderItemID       string   `json:"orderItemId"`
	OrderID           string   `json:"orderId"`
	MenuItemID        string   `json:"menuItemId"`
	NameSnapshot      string   `json:"nameSnapshot"`
	UnitPriceSnapshot int64    `json:"unitPriceSnapshot"` // Satang
	Quantity          float64  `json:"quantity"`
	Notes             *string  `json:"notes,omitempty"`
	Subtotal          int64    `json:"subtotal"`
	KitchenStatus     string   `json:"kitchenStatus"`
	IsBuffetIncluded  bool     `json:"isBuffetIncluded"`
	SelectedModifiers []string `json:"selectedModifiers,omitempty"`
}

// PaymentTransaction represents payment transaction
type PaymentTransaction struct {
	PaymentID      string `json:"paymentId"`
	OrderID        string `json:"orderId"`
	BranchID       string `json:"branchId"`
	DeviceID       string `json:"deviceId,omitempty"`
	ShiftID        string `json:"shiftId,omitempty"`
	PaymentMethod  string `json:"paymentMethod"` // CASH, QR, CARD, EWALLET, VOUCHER
	Amount         int64  `json:"amount"`         // Satang
	TenderedAmount int64  `json:"tenderedAmount"` // Satang
	ChangeAmount   int64  `json:"changeAmount"`   // Satang
	Status         string `json:"status"`         // SUCCESS, REFUNDED
	CreatedBy      string `json:"createdBy,omitempty"`
	CreatedAt      int64  `json:"createdAt"`
}

// TaxInvoiceCustomer represents buyer information for Full Tax Invoice
type TaxInvoiceCustomer struct {
	TaxpayerName string `json:"taxpayerName"`
	TaxID        string `json:"taxId"`
	BranchNumber string `json:"branchNumber"`
	Address      string `json:"address"`
	Phone        string `json:"phone,omitempty"`
}

// API Requests & Responses

type PinLoginRequest struct {
	PinCode  string `json:"pinCode"`
	DeviceID string `json:"deviceId"`
	BranchID string `json:"branchId"`
}

type PinLoginResponse struct {
	Success bool   `json:"success"`
	Message string `json:"message,omitempty"`
	Data    *struct {
		Token string `json:"token"`
		User  User   `json:"user"`
	} `json:"data,omitempty"`
}

type CreateOrderRequest struct {
	BranchID              string             `json:"branchId"`
	TableID               *string            `json:"tableId"`
	OrderType             string             `json:"orderType"`
	Channel               string             `json:"channel"`
	CreatedBy             string             `json:"createdBy"`
	CustomerID            *string            `json:"customerId"`
	BuffetTierID          *string            `json:"buffetTierId"`
	AdultCount            int                `json:"adultCount"`
	ChildCount            int                `json:"childCount"`
	Items                 []CreateOrderItem  `json:"items"`
	ManualDiscountSatang  int64              `json:"manualDiscountSatang"`
	ManualDiscountPercent float64            `json:"manualDiscountPercent"`
	CouponCode            string             `json:"couponCode"`
	PointsRedeemedSatang  int64              `json:"pointsRedeemedSatang"`
}

type CreateOrderItem struct {
	MenuItemID        string   `json:"menuItemId"`
	NameSnapshot      string   `json:"nameSnapshot"`
	UnitPriceSnapshot int64    `json:"unitPriceSnapshot"`
	Quantity          float64  `json:"quantity"`
	Notes             *string  `json:"notes"`
	IsBuffetIncluded  bool     `json:"isBuffetIncluded"`
	SelectedModifiers []string `json:"selectedModifiers"`
}

type ProcessPaymentRequest struct {
	OrderID         string               `json:"orderId"`
	BranchID        string               `json:"branchId"`
	DeviceID        string               `json:"deviceId"`
	AppliedPayments []AppliedPaymentReq  `json:"appliedPayments"`
	TaxCustomer     *TaxInvoiceCustomer  `json:"taxCustomer"`
	CreatedBy       string               `json:"createdBy"`
}

type AppliedPaymentReq struct {
	PaymentMethod  string `json:"paymentMethod"`
	Amount         int64  `json:"amount"` // Satang
	TenderedAmount int64  `json:"tenderedAmount"`
}

type ApiResponse[T any] struct {
	Success bool   `json:"success"`
	Message string `json:"message,omitempty"`
	Data    T      `json:"data,omitempty"`
}

// ── SYNC ENGINE MODELS (Offline-First 2-Way Sync) ──

type SyncQueueItem struct {
	QueueID      string  `json:"queueId"`
	EntityType   string  `json:"entityType"`   // ORDER, PAYMENT, TABLE_STATUS, SHIFT
	EntityID     string  `json:"entityId"`
	Action       string  `json:"action"`       // CREATE, UPDATE, DELETE
	PayloadJSON  string  `json:"payloadJson"`
	Status       string  `json:"status"`       // PENDING, SYNCING, SYNCED, FAILED
	RetryCount   int     `json:"retryCount"`
	ErrorMessage *string `json:"errorMessage,omitempty"`
	CreatedAt    int64   `json:"createdAt"`
	SyncedAt     *int64  `json:"syncedAt,omitempty"`
}

type SyncPushBatchRequest struct {
	BranchID string          `json:"branchId"`
	DeviceID string          `json:"deviceId"`
	Items    []SyncQueueItem `json:"items"`
}

type SyncPushBatchResponse struct {
	ProcessedCount int      `json:"processedCount"`
	SuccessIds     []string `json:"successIds"`
	FailedIds      []string `json:"failedIds"`
}

type SyncPullResponse struct {
	Branch      *Branch        `json:"branch,omitempty"`
	Categories  []MenuCategory `json:"categories"`
	MenuItems   []MenuItem     `json:"menuItems"`
	BuffetTiers []BuffetTier   `json:"buffetTiers"`
	Zones       []Zone         `json:"zones"`
	Tables      []Table        `json:"tables"`
	Promotions  []Promotion    `json:"promotions"`
	Users       []User         `json:"users"`
	ServerTime  any            `json:"serverTime,omitempty"`
}

type SyncStatusInfo struct {
	IsOnline         bool   `json:"isOnline"`
	CloudURL         string `json:"cloudUrl"`
	PendingItemCount int    `json:"pendingItemCount"`
	FailedItemCount  int    `json:"failedItemCount"`
	LastSyncedAt     int64  `json:"lastSyncedAt"`
	LastPullAt       int64  `json:"lastPullAt"`
	LastSyncError    string `json:"lastSyncError,omitempty"`
}

// ── DEVICE IDENTITY MODELS (Multi-Branch POS Activation) ──

type DeviceIdentity struct {
	DeviceID       string `json:"deviceId"`
	BranchID       string `json:"branchId"`
	BranchName     string `json:"branchName"`
	BranchCode     string `json:"branchCode"`
	CompanyID      string `json:"companyId"`
	CompanyName    string `json:"companyName"`
	DeviceName     string `json:"deviceName"`
	DeviceCode     string `json:"deviceCode"`
	ActivatedAt    int64  `json:"activatedAt"`
	ActivationCode string `json:"activationCode"`
	LastVerifiedAt *int64 `json:"lastVerifiedAt,omitempty"`
	CloudApiUrl    string `json:"cloudApiUrl"`
}

type ActivateDeviceRequest struct {
	ActivationCode string `json:"activationCode"`
	CloudApiUrl    string `json:"cloudApiUrl,omitempty"`
}

type ActivateDeviceResponse struct {
	Success  bool            `json:"success"`
	Message  string          `json:"message"`
	Identity *DeviceIdentity `json:"identity,omitempty"`
}

type ActivationCodeRecord struct {
	Code              string  `json:"code"`
	BranchID          string  `json:"branchId"`
	BranchName        string  `json:"branchName"`
	BranchCode        string  `json:"branchCode"`
	DeviceCode        string  `json:"deviceCode"`
	DeviceName        string  `json:"deviceName"`
	CreatedAt         int64   `json:"createdAt"`
	ExpiresAt         int64   `json:"expiresAt"`
	Status            string  `json:"status"` // UNUSED, ACTIVATED, EXPIRED
	ActivatedAt       *int64  `json:"activatedAt,omitempty"`
	ActivatedDeviceID *string `json:"activatedDeviceId,omitempty"`
}

type GenerateActivationCodeRequest struct {
	BranchID       string `json:"branchId,omitempty"`
	BranchName     string `json:"branchName,omitempty"`
	BranchCode     string `json:"branchCode,omitempty"`
	DeviceCode     string `json:"deviceCode,omitempty"`
	DeviceName     string `json:"deviceName,omitempty"`
	ExpiresInHours int    `json:"expiresInHours,omitempty"`
}

// Current time in milliseconds
func NowMillis() int64 {
	return time.Now().UnixNano() / int64(time.Millisecond)
}



