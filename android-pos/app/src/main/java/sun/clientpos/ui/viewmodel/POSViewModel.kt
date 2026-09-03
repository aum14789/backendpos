package sun.clientpos.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sun.clientpos.BuildConfig
import sun.clientpos.data.local.PosDatabase
import sun.clientpos.data.local.PosDatabaseSeeder
import sun.clientpos.data.local.entity.*
import sun.clientpos.data.remote.PinLoginRequest
import sun.clientpos.data.remote.RetrofitClient
import sun.clientpos.data.repository.*
import sun.clientpos.domain.pricing.PricingCalculationResult
import sun.clientpos.domain.pricing.PricingEngine
import sun.clientpos.domain.pricing.PricingItemInput
import sun.clientpos.printer.*
import sun.clientpos.sync.ConnectionStatus
import sun.clientpos.sync.OfflineSyncManager
import sun.clientpos.sync.POSSyncState
import sun.clientpos.ui.pos.AppliedPayment
import sun.clientpos.ui.pos.CartItem

data class CustomerUiState(
    val id: String,
    val firstName: String,
    val lastName: String? = null,
    val phone: String,
    val memberId: String? = null,
    val lineId: String? = null,
    val email: String? = null,
    val tierCode: String = "SILVER",
    val tierName: String = "สมาชิกทั่วไป (Silver)",
    val discountPercentage: Double = 0.0,
    val pointMultiplier: Double = 1.0,
    val pointsBalance: Double = 0.0
) {
    val fullName: String
        get() = if (lastName.isNullOrBlank()) firstName else "$firstName $lastName"
}

data class AppliedCouponUiState(
    val code: String,
    val name: String,
    val discountSatang: Long
)

data class CurrentOrderUiState(
    val orderType: String, // DINE_IN, BUFFET, TAKEAWAY, DELIVERY
    val table: RoomTableEntity? = null,
    val buffetTier: RoomBuffetTierEntity? = null,
    val buffetAdultCount: Int = 2,
    val buffetChildCount: Int = 0,
    val deliveryCustomerName: String? = null,
    val deliveryCustomerPhone: String? = null,
    val createdOrder: RoomOrderEntity? = null,
    val buffetSession: RoomBuffetSessionEntity? = null,
    val pricingResult: PricingCalculationResult? = null,
    val cartItems: List<CartItem> = emptyList(),
    val customer: CustomerUiState? = null,
    val appliedCoupon: AppliedCouponUiState? = null,
    val pointsRedeemedSatang: Long = 0L
)

data class CompletedReceiptUiState(
    val order: RoomOrderEntity,
    val items: List<ReceiptItemLine>,
    val payments: List<ReceiptPaymentLine>,
    val buffetTierName: String?,
    val buffetAdults: Int,
    val buffetChildren: Int,
    val buffetHeadChargeSatang: Long,
    val changeSatang: Long,
    val taxCustomer: TaxInvoiceCustomer?,
    val customer: CustomerUiState? = null,
    val earnedPoints: Long = 0L,
    val appliedCoupon: AppliedCouponUiState? = null
)

class POSViewModel(application: Application) : AndroidViewModel(application) {

    val db = PosDatabase.getDatabase(application)

    private val authRepo = OfflineAuthRepository(db.userDao(), db.permissionDao())
    private val orderRepo = OrderRepository(db.orderDao(), db.syncOutboxDao(), db.buffetDao(), db.promotionDao())
    private val paymentRepo = PaymentRepository(db.paymentDao(), db.orderDao(), db.syncOutboxDao())
    val syncManager = OfflineSyncManager(db.syncOutboxDao(), db.menuDao(), db.deviceCapabilityDao())
    val printerService = PrinterService()
    val connectivityChecker = sun.clientpos.domain.ConnectivityChecker(application)

    val branchId = BuildConfig.BRANCH_ID
    val deviceId = BuildConfig.DEVICE_ID

    // ── Network & Device Capabilities ──
    val isOnline: StateFlow<Boolean> = connectivityChecker.isOnline

    val activeCapabilities: StateFlow<List<DeviceCapabilityEntity>> = db.deviceCapabilityDao()
        .getActiveCapabilities(deviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun hasCapability(cap: String): Boolean {
        val caps = activeCapabilities.value
        if (caps.isEmpty()) return true
        return caps.any { it.capability.equals(cap, ignoreCase = true) && it.isActive }
    }

    val canPay: Boolean get() = hasCapability("PAY")
    val canOpenTable: Boolean get() = hasCapability("OPEN_TABLE")
    val canTakeOrder: Boolean get() = hasCapability("TAKE_ORDER")
    val canOpenShift: Boolean get() = hasCapability("OPEN_SHIFT")
    val canCloseShift: Boolean get() = hasCapability("CLOSE_SHIFT")
    val canPrintReceipt: Boolean get() = hasCapability("PRINT_RECEIPT")
    val canCloseBusinessDay: Boolean get() = hasCapability("CLOSE_BUSINESS_DAY")
    val canStockAdjust: Boolean get() = hasCapability("STOCK_ADJUST")

    // CRM Offline Policy: Redeem points and coupon require cloud internet connectivity
    val canRedeemPoints: Boolean get() = isOnline.value && canPay
    val canUseCoupon: Boolean get() = isOnline.value

    // ── Observable UI States ──
    private val _authenticatedUser = MutableStateFlow<AuthenticatedUserSession?>(null)
    val authenticatedUser: StateFlow<AuthenticatedUserSession?> = _authenticatedUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = syncManager.connectionStatus
    val syncState: StateFlow<POSSyncState> = syncManager.posSyncState

    val zones: StateFlow<List<RoomZoneEntity>> = db.tableDao()
        .observeZones(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tables: StateFlow<List<RoomTableEntity>> = db.tableDao()
        .observeTablesByBranch(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeOrders: StateFlow<List<RoomOrderEntity>> = db.orderDao()
        .observeActiveOrders(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBuffetSessions: StateFlow<List<RoomBuffetSessionEntity>> = db.buffetDao()
        .observeActiveSessions(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<RoomMenuCategoryEntity>> = db.menuDao()
        .observeCategories(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val menuItems: StateFlow<List<RoomMenuItemEntity>> = db.menuDao()
        .observeAllMenuItems(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buffetTiers: StateFlow<List<RoomBuffetTierEntity>> = db.buffetDao()
        .observeActiveTiersByBranch(branchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _eligibleBuffetItemIds = MutableStateFlow<Set<String>>(emptySet())
    val eligibleBuffetItemIds: StateFlow<Set<String>> = _eligibleBuffetItemIds.asStateFlow()

    private val _currentOrderState = MutableStateFlow<CurrentOrderUiState?>(null)
    val currentOrderState: StateFlow<CurrentOrderUiState?> = _currentOrderState.asStateFlow()

    private val _completedReceipt = MutableStateFlow<CompletedReceiptUiState?>(null)
    val completedReceipt: StateFlow<CompletedReceiptUiState?> = _completedReceipt.asStateFlow()

    // ── Phase 2 CRM States ──
    private val _selectedCustomer = MutableStateFlow<CustomerUiState?>(null)
    val selectedCustomer: StateFlow<CustomerUiState?> = _selectedCustomer.asStateFlow()

    private val _appliedCoupon = MutableStateFlow<AppliedCouponUiState?>(null)
    val appliedCoupon: StateFlow<AppliedCouponUiState?> = _appliedCoupon.asStateFlow()

    private val _redeemedPointsSatang = MutableStateFlow<Long>(0L)
    val redeemedPointsSatang: StateFlow<Long> = _redeemedPointsSatang.asStateFlow()

    // Demo VIP Customers list for instant fallback lookup
    private val demoCustomers = listOf(
        CustomerUiState(
            id = "cust-001",
            firstName = "สมชาย",
            lastName = "ประเสริฐสุข",
            phone = "081-111-2222",
            memberId = "MEM-10001",
            email = "somchai@sunpos.com",
            tierCode = "GOLD",
            tierName = "สมาชิก VIP (Gold)",
            discountPercentage = 5.0,
            pointMultiplier = 1.5,
            pointsBalance = 500.0
        ),
        CustomerUiState(
            id = "cust-002",
            firstName = "สมศักดิ์",
            lastName = "ดีงาม",
            phone = "081-333-4444",
            memberId = "MEM-10002",
            tierCode = "SILVER",
            tierName = "สมาชิกทั่วไป (Silver)",
            discountPercentage = 0.0,
            pointMultiplier = 1.0,
            pointsBalance = 100.0
        ),
        CustomerUiState(
            id = "cust-003",
            firstName = "แอน",
            lastName = "สุขใจ",
            phone = "089-999-8888",
            memberId = "MEM-10003",
            lineId = "ann_sukjai",
            tierCode = "PLATINUM",
            tierName = "สมาชิก VVIP (Platinum)",
            discountPercentage = 10.0,
            pointMultiplier = 2.0,
            pointsBalance = 1500.0
        )
    )

    init {
        viewModelScope.launch {
            // Seed database if clean install or missing components
            PosDatabaseSeeder.seedIfEmpty(db)
            RetrofitClient.initialize(BuildConfig.BACKEND_BASE_URL, deviceId, branchId)

            val zoneCount = db.tableDao().getZoneCount()
            val tableCount = db.tableDao().getTableCount()
            val tierCount = db.buffetDao().getTierCount()
            val itemCount = db.menuDao().getMenuItemCount()
            android.util.Log.i("POSViewModel", "Local DB Ready: $zoneCount zones, $tableCount tables, $tierCount buffet tiers, $itemCount menu items (branchId: $branchId)")
        }

        // Periodic Background Sync Loop: Every 30 seconds when Online
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                if (syncManager.connectionStatus.value == ConnectionStatus.ONLINE) {
                    syncManager.pushPendingEventsBatch()
                }
            }
        }
    }

    // ── Authentication ──

    fun loginWithPin(pin: String, onLoginSuccess: () -> Unit) {
        viewModelScope.launch {
            _loginError.value = null

            // 1. Try Online Login via Retrofit if online
            if (connectionStatus.value == ConnectionStatus.ONLINE) {
                try {
                    val api = RetrofitClient.getSyncApiService()
                    val response = api.pinLogin(PinLoginRequest(pinCode = pin, deviceId = deviceId, branchId = branchId))
                    if (response.success && response.data != null) {
                        val remoteUser = response.data.user
                        RetrofitClient.setToken(response.data.token)

                        val session = AuthenticatedUserSession(
                            userId = remoteUser.userId,
                            username = remoteUser.username,
                            fullName = remoteUser.fullName,
                            permissions = remoteUser.permissions
                        )
                        _authenticatedUser.value = session
                        onLoginSuccess()
                        return@launch
                    }
                } catch (_: Exception) {
                    // Fall back to offline verification
                }
            }

            // 2. Offline Verification against Room Cached BCrypt Hashes
            val result = authRepo.authenticatePinOffline(pin)
            result.onSuccess { session ->
                _authenticatedUser.value = session
                onLoginSuccess()
            }.onFailure { error ->
                _loginError.value = error.message ?: "Invalid PIN code"
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        _authenticatedUser.value = null
        RetrofitClient.setToken(null)
        _currentOrderState.value = null
        _selectedCustomer.value = null
        _appliedCoupon.value = null
        _redeemedPointsSatang.value = 0L
        onLoggedOut()
    }

    // ── CRM / Customer Functions ──

    fun searchCustomers(query: String): List<CustomerUiState> {
        val q = query.trim()
        val norm = sun.clientpos.common.PhoneUtils.normalize(q)
        if (q.isBlank()) return demoCustomers
        return demoCustomers.filter {
            val custNormPhone = sun.clientpos.common.PhoneUtils.normalize(it.phone)
            custNormPhone.contains(norm) ||
            it.phone.contains(q, ignoreCase = true) ||
            (it.memberId?.contains(q, ignoreCase = true) == true) ||
            (it.lineId?.contains(q, ignoreCase = true) == true) ||
            it.fullName.contains(q, ignoreCase = true)
        }
    }

    fun registerLocalCustomer(displayName: String, phone: String, customerGroup: String = "GENERAL"): CustomerUiState {
        val custId = java.util.UUID.randomUUID().toString()
        val normPhone = sun.clientpos.common.PhoneUtils.normalize(phone)
        val entity = RoomCustomerEntity(
            customerId = custId,
            displayName = displayName.trim(),
            phone = normPhone,
            customerGroup = customerGroup,
            isSynced = false
        )
        viewModelScope.launch {
            db.customerDao().insertCustomer(entity)

            // Queue CUSTOMER_UPSERTED in Sync Outbox
            val payload = """
                {
                    "customerId": "$custId",
                    "displayName": "${displayName.trim()}",
                    "phone": "$normPhone",
                    "customerGroup": "$customerGroup"
                }
            """.trimIndent()

            db.syncOutboxDao().insertEvent(
                SyncOutboxEntity(
                    eventId = java.util.UUID.randomUUID().toString(),
                    aggregateType = "CUSTOMER",
                    aggregateId = custId,
                    eventType = "CUSTOMER_UPSERTED",
                    payload = payload,
                    deviceId = deviceId,
                    branchId = branchId,
                    status = SyncStatus.PENDING
                )
            )

            if (connectionStatus.value == ConnectionStatus.ONLINE) {
                syncManager.pushPendingEventsBatch()
            }
        }

        val state = CustomerUiState(
            id = custId,
            firstName = displayName.trim(),
            phone = normPhone
        )
        _selectedCustomer.value = state
        return state
    }

    fun selectCustomer(customer: CustomerUiState?) {
        _selectedCustomer.value = customer
    }

    fun clearCustomer() {
        _selectedCustomer.value = null
        _redeemedPointsSatang.value = 0L
    }

    fun applyCouponWithReason(code: String, orderAmountSatang: Long): String? {
        // Enforce CRM Offline Policy: Coupon validation requires Cloud internet connectivity
        if (!canUseCoupon) {
            return "ระบบออฟไลน์: ต้องเชื่อมต่ออินเทอร์เน็ตเพื่อตรวจสอบและใช้คูปอง"
        }

        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) {
            return "กรุณาระบุรหัสคูปอง"
        }
        return when (trimmed) {
            "WELCOME50" -> {
                if (orderAmountSatang >= 20000L) { // Min ฿200
                    _appliedCoupon.value = AppliedCouponUiState("WELCOME50", "คูปองต้อนรับ ฿50", 5000L)
                    null
                } else {
                    "ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿200.00 (ปัจจุบัน ฿${orderAmountSatang / 100})"
                }
            }
            "SUNVIP10" -> {
                if (orderAmountSatang >= 30000L) { // Min ฿300
                    val rawDiscount = (orderAmountSatang * 0.10).toLong()
                    val cappedDiscount = rawDiscount.coerceAtMost(10000L) // Max discount ฿100 (10,000 satang)
                    _appliedCoupon.value = AppliedCouponUiState("SUNVIP10", "คูปองสมาชิก VIP 10%", cappedDiscount)
                    null
                } else {
                    "ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿300.00 (ปัจจุบัน ฿${orderAmountSatang / 100})"
                }
            }
            "DISCOUNT100" -> {
                if (orderAmountSatang >= 50000L) { // Min ฿500
                    _appliedCoupon.value = AppliedCouponUiState("DISCOUNT100", "คูปองส่วนลดพิเศษ ฿100", 10000L)
                    null
                } else {
                    "ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿500.00 (ปัจจุบัน ฿${orderAmountSatang / 100})"
                }
            }
            "SHABU100" -> {
                if (orderAmountSatang >= 70000L) {
                    _appliedCoupon.value = AppliedCouponUiState("SHABU100", "ส่วนลดชาบู ฿100", 10000L)
                    null
                } else {
                    "ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿700.00 (ปัจจุบัน ฿${orderAmountSatang / 100})"
                }
            }
            "EXPIRED20" -> {
                "คูปองหมดอายุแล้ว"
            }
            else -> {
                "ไม่พบคูปองรหัส '$trimmed' ในระบบ หรือคูปองไม่สามารถใช้งานได้"
            }
        }
    }

    fun applyCoupon(code: String, orderAmountSatang: Long): Boolean {
        return applyCouponWithReason(code, orderAmountSatang) == null
    }

    fun removeCoupon() {
        _appliedCoupon.value = null
    }

    fun setRedeemedPoints(pointsSatang: Long) {
        // Enforce CRM Offline Policy & Device Capability
        if (pointsSatang > 0L && !canRedeemPoints) {
            _redeemedPointsSatang.value = 0L
            return
        }
        _redeemedPointsSatang.value = pointsSatang
    }

    // ── Order Flow Setup & Table Management ──

    fun openDineInTable(table: RoomTableEntity, guestCount: Int, onOpened: () -> Unit) {
        viewModelScope.launch {
            val user = _authenticatedUser.value
            val (order, pricing) = orderRepo.createOrderLocal(
                branchId = branchId,
                tableId = table.tableId,
                tableSessionId = null,
                orderType = "DINE_IN",
                channel = "POS",
                createdBy = user?.username,
                items = emptyList(),
                deviceId = deviceId
            )

            db.tableDao().updateTableStatus(table.tableId, "OCCUPIED")

            _currentOrderState.value = CurrentOrderUiState(
                orderType = "DINE_IN",
                table = table.copy(status = "OCCUPIED"),
                createdOrder = order,
                pricingResult = pricing,
                cartItems = emptyList()
            )

            onOpened()
        }
    }

    fun openBuffetTable(
        table: RoomTableEntity,
        tier: RoomBuffetTierEntity,
        adults: Int,
        children: Int,
        onOpened: () -> Unit
    ) {
        viewModelScope.launch {
            val user = _authenticatedUser.value
            val eligibleIds = db.buffetDao().getEligibleMenuItemIds(tier.tierId).toSet()
            _eligibleBuffetItemIds.value = eligibleIds

            val (buffetOrder, buffetSession, pricingResult) = orderRepo.createBuffetOrderLocal(
                branchId = branchId,
                tableId = table.tableId,
                tableSessionId = null,
                buffetTier = tier,
                adultCount = adults,
                childCount = children,
                channel = "POS",
                createdBy = user?.username,
                items = emptyList(),
                deviceId = deviceId
            )

            db.tableDao().updateTableStatus(table.tableId, "OCCUPIED")

            _currentOrderState.value = CurrentOrderUiState(
                orderType = "BUFFET",
                table = table.copy(status = "OCCUPIED"),
                createdOrder = buffetOrder,
                buffetSession = buffetSession,
                pricingResult = pricingResult,
                buffetTier = tier,
                buffetAdultCount = adults,
                buffetChildCount = children,
                cartItems = emptyList()
            )

            onOpened()
        }
    }

    fun startDineInOrder(table: RoomTableEntity) {
        selectOrOpenTable(table)
    }

    fun selectOrOpenTable(table: RoomTableEntity, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            val allActive = db.orderDao().observeActiveOrders(branchId).first()
            val activeOrder = allActive.firstOrNull { it.tableId == table.tableId }
            if (activeOrder != null) {
                val orderItems = db.orderDao().getOrderItems(activeOrder.orderId)
                val allMenus = menuItems.value
                val loadedCartItems = orderItems.map { oi ->
                    val menuItem = allMenus.find { it.itemId == oi.menuItemId } ?: RoomMenuItemEntity(
                        itemId = oi.menuItemId,
                        branchId = branchId,
                        categoryId = "",
                        name = oi.nameSnapshot,
                        description = null,
                        sku = null,
                        basePrice = oi.unitPriceSnapshot
                    )
                    CartItem(
                        item = menuItem,
                        quantity = oi.quantity,
                        isBuffetIncluded = oi.kitchenStatus == "BUFFET_INCLUDED" || activeOrder.orderType == "BUFFET"
                    )
                }
                val custEntity = if (activeOrder.customerId != null) db.customerDao().getCustomerById(activeOrder.customerId) else null
                val custUi = custEntity?.let { CustomerUiState(it.customerId, it.displayName, null, it.phone, it.memberId, it.lineId, it.email, it.tierCode, it.tierName, it.discountPercent, 1.0, it.pointsBalance) }

                val buffetSession = if (activeOrder.orderType == "BUFFET") db.buffetDao().getSessionByOrder(activeOrder.orderId) else null
                val buffetTier = buffetSession?.let { db.buffetDao().getTierById(it.buffetTierId) }
                if (buffetTier != null) {
                    val eligibleIds = db.buffetDao().getEligibleMenuItemIds(buffetTier.tierId).toSet()
                    _eligibleBuffetItemIds.value = eligibleIds
                }

                _selectedCustomer.value = custUi
                _currentOrderState.value = CurrentOrderUiState(
                    orderType = activeOrder.orderType,
                    table = table,
                    createdOrder = activeOrder,
                    buffetSession = buffetSession,
                    buffetTier = buffetTier,
                    buffetAdultCount = buffetSession?.adultCount ?: 0,
                    buffetChildCount = buffetSession?.childCount ?: 0,
                    cartItems = loadedCartItems,
                    customer = custUi
                )
            } else {
                _currentOrderState.value = CurrentOrderUiState(
                    orderType = "DINE_IN",
                    table = table
                )
            }
            onOpened()
        }
    }

    fun startTakeawayOrder() {
        _currentOrderState.value = CurrentOrderUiState(
            orderType = "TAKEAWAY"
        )
    }

    fun startDeliveryOrder(customerName: String, customerPhone: String) {
        _currentOrderState.value = CurrentOrderUiState(
            orderType = "DELIVERY",
            deliveryCustomerName = customerName,
            deliveryCustomerPhone = customerPhone
        )
    }

    fun startBuffetOrder(tier: RoomBuffetTierEntity, adults: Int, children: Int, table: RoomTableEntity?) {
        viewModelScope.launch {
            val eligibleIds = db.buffetDao().getEligibleMenuItemIds(tier.tierId).toSet()
            _eligibleBuffetItemIds.value = eligibleIds

            _currentOrderState.value = CurrentOrderUiState(
                orderType = "BUFFET",
                table = table,
                buffetTier = tier,
                buffetAdultCount = adults,
                buffetChildCount = children
            )
        }
    }

    // ── Send to Kitchen (Save/Print Kitchen Ticket, Keep Table Occupied) ──

    fun sendToKitchen(
        cartItems: List<CartItem>,
        orderType: String,
        buffetTier: RoomBuffetTierEntity?,
        adults: Int,
        children: Int,
        manualDiscountSatang: Long,
        manualDiscountPercent: Double = 0.0,
        onSent: () -> Unit
    ) {
        viewModelScope.launch {
            val user = _authenticatedUser.value
            val current = _currentOrderState.value
            val cust = _selectedCustomer.value
            val coup = _appliedCoupon.value
            val ptsSatang = _redeemedPointsSatang.value
            val table = current?.table

            val localItems = cartItems.map { c ->
                LocalOrderItemRequest(
                    menuItemId = c.item.itemId,
                    nameSnapshot = c.item.name,
                    unitPriceSnapshot = c.item.basePrice,
                    quantity = c.quantity,
                    isBuffetIncluded = c.isBuffetIncluded
                )
            }

            val discountOpts = OrderPricingDiscountOptions(
                manualDiscountSatang = manualDiscountSatang,
                manualDiscountPercent = manualDiscountPercent,
                memberDiscountPercent = cust?.discountPercentage ?: 0.0,
                couponDiscountSatang = (coup?.discountSatang ?: 0L) + ptsSatang,
                isVatInclusive = true
            )

            val (order, pricing) = if (orderType == "BUFFET" && buffetTier != null) {
                val (bOrder, session, bPricing) = orderRepo.createBuffetOrderLocal(
                    branchId = branchId,
                    tableId = table?.tableId,
                    tableSessionId = null,
                    buffetTier = buffetTier,
                    adultCount = adults,
                    childCount = children,
                    channel = "POS",
                    createdBy = user?.username,
                    items = localItems,
                    discountOptions = discountOpts,
                    deviceId = deviceId,
                    customerId = cust?.id
                )
                _currentOrderState.value = current?.copy(
                    createdOrder = bOrder,
                    buffetSession = session,
                    pricingResult = bPricing,
                    cartItems = cartItems,
                    customer = cust,
                    appliedCoupon = coup,
                    pointsRedeemedSatang = ptsSatang
                )
                Pair(bOrder, bPricing)
            } else {
                val (stdOrder, stdPricing) = orderRepo.createOrderLocal(
                    branchId = branchId,
                    tableId = table?.tableId,
                    tableSessionId = null,
                    orderType = orderType,
                    channel = "POS",
                    createdBy = user?.username,
                    items = localItems,
                    discountOptions = discountOpts,
                    deviceId = deviceId,
                    customerId = cust?.id
                )
                _currentOrderState.value = current?.copy(
                    createdOrder = stdOrder,
                    pricingResult = stdPricing,
                    cartItems = cartItems,
                    customer = cust,
                    appliedCoupon = coup,
                    pointsRedeemedSatang = ptsSatang
                )
                Pair(stdOrder, stdPricing)
            }

            // Update Table Status to OCCUPIED in database
            if (table != null) {
                db.tableDao().updateTableStatus(table.tableId, "OCCUPIED")
            }

            // Send Kitchen Ticket to printer
            printKitchenTicket(order, cartItems, table?.nameNumber, orderType)

            if (connectionStatus.value == ConnectionStatus.ONLINE) {
                syncManager.pushPendingEventsBatch()
            }

            onSent()
        }
    }

    // ── Proceed to Checkout (Prepare for Payment, Set Table to WAITING_PAYMENT) ──

    fun proceedToCheckout(
        cartItems: List<CartItem>,
        orderType: String,
        buffetTier: RoomBuffetTierEntity?,
        adults: Int,
        children: Int,
        manualDiscountSatang: Long,
        manualDiscountPercent: Double = 0.0,
        onProceedToPayment: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val user = _authenticatedUser.value
            val current = _currentOrderState.value
            val cust = _selectedCustomer.value
            val coup = _appliedCoupon.value
            val ptsSatang = _redeemedPointsSatang.value
            val table = current?.table

            val localItems = cartItems.map { c ->
                LocalOrderItemRequest(
                    menuItemId = c.item.itemId,
                    nameSnapshot = c.item.name,
                    unitPriceSnapshot = c.item.basePrice,
                    quantity = c.quantity,
                    isBuffetIncluded = c.isBuffetIncluded
                )
            }

            val discountOpts = OrderPricingDiscountOptions(
                manualDiscountSatang = manualDiscountSatang,
                manualDiscountPercent = manualDiscountPercent,
                memberDiscountPercent = cust?.discountPercentage ?: 0.0,
                couponDiscountSatang = (coup?.discountSatang ?: 0L) + ptsSatang,
                isVatInclusive = true
            )

            val (order, pricing) = if (orderType == "BUFFET" && buffetTier != null) {
                val (bOrder, session, bPricing) = orderRepo.createBuffetOrderLocal(
                    branchId = branchId,
                    tableId = table?.tableId,
                    tableSessionId = null,
                    buffetTier = buffetTier,
                    adultCount = adults,
                    childCount = children,
                    channel = "POS",
                    createdBy = user?.username,
                    items = localItems,
                    discountOptions = discountOpts,
                    deviceId = deviceId,
                    customerId = cust?.id
                )
                _currentOrderState.value = current?.copy(
                    createdOrder = bOrder,
                    buffetSession = session,
                    pricingResult = bPricing,
                    cartItems = cartItems,
                    customer = cust,
                    appliedCoupon = coup,
                    pointsRedeemedSatang = ptsSatang
                )
                Pair(bOrder, bPricing)
            } else {
                val (stdOrder, stdPricing) = orderRepo.createOrderLocal(
                    branchId = branchId,
                    tableId = table?.tableId,
                    tableSessionId = null,
                    orderType = orderType,
                    channel = "POS",
                    createdBy = user?.username,
                    items = localItems,
                    discountOptions = discountOpts,
                    deviceId = deviceId,
                    customerId = cust?.id
                )
                _currentOrderState.value = current?.copy(
                    createdOrder = stdOrder,
                    pricingResult = stdPricing,
                    cartItems = cartItems,
                    customer = cust,
                    appliedCoupon = coup,
                    pointsRedeemedSatang = ptsSatang
                )
                Pair(stdOrder, stdPricing)
            }

            // Update Table Status to WAITING_PAYMENT
            if (table != null) {
                db.tableDao().updateTableStatus(table.tableId, "WAITING_PAYMENT")
            }

            if (connectionStatus.value == ConnectionStatus.ONLINE) {
                syncManager.pushPendingEventsBatch()
            }

            onProceedToPayment(pricing.grandTotal)
        }
    }

    // ── Confirm Order (Legacy & Direct Payment Support) ──

    fun confirmOrder(
        cartItems: List<CartItem>,
        orderType: String,
        buffetTier: RoomBuffetTierEntity?,
        adults: Int,
        children: Int,
        manualDiscountSatang: Long,
        manualDiscountPercent: Double = 0.0,
        onProceedToPayment: (Long) -> Unit
    ) {
        proceedToCheckout(
            cartItems = cartItems,
            orderType = orderType,
            buffetTier = buffetTier,
            adults = adults,
            children = children,
            manualDiscountSatang = manualDiscountSatang,
            manualDiscountPercent = manualDiscountPercent,
            onProceedToPayment = onProceedToPayment
        )
    }

    // ── Complete Payment & Receipt ──

    fun completePayment(
        appliedPayments: List<AppliedPayment>,
        taxCustomer: TaxInvoiceCustomer?,
        onPaymentSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val current = _currentOrderState.value ?: return@launch
            val order = current.createdOrder ?: return@launch
            val user = _authenticatedUser.value
            val cust = current.customer ?: _selectedCustomer.value

            val paymentLines = mutableListOf<ReceiptPaymentLine>()
            var totalTendered = 0L

            for (p in appliedPayments) {
                paymentRepo.processPaymentLocal(
                    orderId = order.orderId,
                    branchId = branchId,
                    deviceId = deviceId,
                    shiftId = null,
                    paymentMethod = p.method,
                    amount = p.amount,
                    tenderedAmount = p.amount,
                    orderTotalAmount = order.totalAmount,
                    createdBy = user?.username
                )
                paymentLines.add(ReceiptPaymentLine(method = p.method, amountSatang = p.amount))
                totalTendered += p.amount
            }

            val changeSatang = (totalTendered - order.totalAmount).coerceAtLeast(0L)

            val receiptItems = current.cartItems.map {
                ReceiptItemLine(
                    name = it.item.name,
                    quantity = it.quantity,
                    unitPriceSatang = it.item.basePrice,
                    subtotalSatang = it.subtotal,
                    isBuffetIncluded = it.isBuffetIncluded
                )
            }

            val buffetHeadCharge = current.buffetSession?.totalChargeSatang() ?: 0L

            // Calculate points earned: 1 point per 25 THB net spent * multiplier
            val earnedPoints = if (cust != null) {
                val spentBaht = order.totalAmount / 100.0
                ((spentBaht / 25.0) * cust.pointMultiplier).toLong()
            } else 0L

            // Enqueue CRM & Loyalty events in Sync Outbox
            if (cust != null) {
                // 1. ORDER_CUSTOMER_LINKED Event
                val linkPayload = """
                    {
                        "orderId": "${order.orderId}",
                        "customerId": "${cust.id}"
                    }
                """.trimIndent()
                db.syncOutboxDao().insertEvent(
                    SyncOutboxEntity(
                        eventId = java.util.UUID.randomUUID().toString(),
                        aggregateType = "ORDER",
                        aggregateId = order.orderId,
                        eventType = "ORDER_CUSTOMER_LINKED",
                        payload = linkPayload,
                        deviceId = deviceId,
                        branchId = branchId,
                        status = SyncStatus.PENDING
                    )
                )

                // 2. POINT_EARNED Event
                if (earnedPoints > 0L) {
                    val earnPayload = """
                        {
                            "customerId": "${cust.id}",
                            "orderId": "${order.orderId}",
                            "orderAmountSatang": ${order.totalAmount},
                            "earnedPoints": $earnedPoints,
                            "multiplier": ${cust.pointMultiplier}
                        }
                    """.trimIndent()
                    db.syncOutboxDao().insertEvent(
                        SyncOutboxEntity(
                            eventId = java.util.UUID.randomUUID().toString(),
                            aggregateType = "LOYALTY_POINT",
                            aggregateId = cust.id,
                            eventType = "POINT_EARNED",
                            payload = earnPayload,
                            deviceId = deviceId,
                            branchId = branchId,
                            status = SyncStatus.PENDING
                        )
                    )
                }

                // 3. POINT_REDEEMED Event
                if (current.pointsRedeemedSatang > 0L) {
                    val ptsRedeemed = current.pointsRedeemedSatang / 10
                    val redeemPayload = """
                        {
                            "customerId": "${cust.id}",
                            "orderId": "${order.orderId}",
                            "pointsRedeemed": $ptsRedeemed,
                            "discountAmountSatang": ${current.pointsRedeemedSatang}
                        }
                    """.trimIndent()
                    db.syncOutboxDao().insertEvent(
                        SyncOutboxEntity(
                            eventId = java.util.UUID.randomUUID().toString(),
                            aggregateType = "LOYALTY_POINT",
                            aggregateId = cust.id,
                            eventType = "POINT_REDEEMED",
                            payload = redeemPayload,
                            deviceId = deviceId,
                            branchId = branchId,
                            status = SyncStatus.PENDING
                        )
                    )
                }

                // 4. COUPON_REDEEMED Event
                if (current.appliedCoupon != null) {
                    val coup = current.appliedCoupon
                    val couponPayload = """
                        {
                            "code": "${coup.code}",
                            "orderId": "${order.orderId}",
                            "orderAmountSatang": ${order.totalAmount},
                            "discountAmountSatang": ${coup.discountSatang},
                            "customerId": "${cust.id}"
                        }
                    """.trimIndent()
                    db.syncOutboxDao().insertEvent(
                        SyncOutboxEntity(
                            eventId = java.util.UUID.randomUUID().toString(),
                            aggregateType = "COUPON",
                            aggregateId = coup.code,
                            eventType = "COUPON_REDEEMED",
                            payload = couponPayload,
                            deviceId = deviceId,
                            branchId = branchId,
                            status = SyncStatus.PENDING
                        )
                    )
                }
            }

            val completedState = CompletedReceiptUiState(
                order = order,
                items = receiptItems,
                payments = paymentLines,
                buffetTierName = current.buffetTier?.name,
                buffetAdults = current.buffetAdultCount,
                buffetChildren = current.buffetChildCount,
                buffetHeadChargeSatang = buffetHeadCharge,
                changeSatang = changeSatang,
                taxCustomer = taxCustomer,
                customer = cust,
                earnedPoints = earnedPoints,
                appliedCoupon = current.appliedCoupon
            )

            _completedReceipt.value = completedState

            // Auto-print receipt
            printReceipt(completedState, isTaxInvoice = taxCustomer != null)

            // Update order status to COMPLETED
            db.orderDao().updateOrderStatus(order.orderId, "COMPLETED")

            // Release table back to AVAILABLE
            if (order.tableId != null) {
                db.tableDao().updateTableStatus(order.tableId, "AVAILABLE")
                if (order.tableSessionId != null) {
                    db.tableSessionDao().closeSession(order.tableSessionId, System.currentTimeMillis(), user?.username)
                }
            }

            if (connectionStatus.value == ConnectionStatus.ONLINE) {
                syncManager.pushPendingEventsBatch()
            }

            onPaymentSuccess()
        }
    }

    // ── Printing Helpers ──

    private fun printKitchenTicket(order: RoomOrderEntity, cartItems: List<CartItem>, tableNumber: String?, orderType: String) {
        viewModelScope.launch {
            val ticketItems = cartItems.map {
                KitchenTicketItem(
                    name = it.item.name,
                    quantity = it.quantity,
                    modifiers = it.selectedModifiers
                )
            }
            val rawBytes = KitchenTicketBuilder.buildKitchenTicket(
                tableNumber = tableNumber,
                orderNumber = order.orderNumber,
                orderType = orderType,
                serverName = _authenticatedUser.value?.fullName,
                items = ticketItems
            )
            printerService.print(rawBytes)
        }
    }

    fun printReceipt(receipt: CompletedReceiptUiState, isTaxInvoice: Boolean = false) {
        viewModelScope.launch {
            val rawBytes = if (isTaxInvoice && receipt.taxCustomer != null) {
                ReceiptBuilder.buildFullTaxInvoiceReceipt(
                    invoiceNumber = "INV-${receipt.order.orderNumber}",
                    companyName = "SunPOS Restaurant Group",
                    branchName = "Sukhumvit Main Branch",
                    branchCode = "BR-01",
                    companyTaxId = "0105560000001",
                    companyAddress = "123 Sukhumvit Road, Bangkok",
                    posDeviceId = deviceId,
                    cashierName = _authenticatedUser.value?.fullName ?: "Cashier",
                    orderNumber = receipt.order.orderNumber,
                    customer = receipt.taxCustomer,
                    items = receipt.items,
                    buffetHeadChargeSatang = receipt.buffetHeadChargeSatang,
                    grossSatang = receipt.order.subtotalAmount,
                    discountSatang = receipt.order.discountAmount,
                    taxSatang = receipt.order.taxAmount,
                    grandTotalSatang = receipt.order.totalAmount,
                    payments = receipt.payments
                )
            } else {
                ReceiptBuilder.buildAbbreviatedReceipt(
                    companyName = "SunPOS Restaurant Group",
                    branchName = "Sukhumvit Main Branch",
                    branchCode = "BR-01",
                    companyTaxId = "0105560000001",
                    posDeviceId = deviceId,
                    cashierName = _authenticatedUser.value?.fullName ?: "Cashier",
                    orderNumber = receipt.order.orderNumber,
                    tableNumber = receipt.order.tableId,
                    orderType = receipt.order.orderType,
                    buffetTierName = receipt.buffetTierName,
                    buffetAdults = receipt.buffetAdults,
                    buffetChildren = receipt.buffetChildren,
                    buffetHeadChargeSatang = receipt.buffetHeadChargeSatang,
                    items = receipt.items,
                    grossSatang = receipt.order.subtotalAmount,
                    discountSatang = receipt.order.discountAmount,
                    taxSatang = receipt.order.taxAmount,
                    grandTotalSatang = receipt.order.totalAmount,
                    payments = receipt.payments,
                    changeSatang = receipt.changeSatang
                )
            }
            printerService.print(rawBytes)
        }
    }

    // ── Sync Actions ──

    fun triggerManualSync() {
        viewModelScope.launch {
            syncManager.pushPendingEventsBatch()
            syncManager.pullMasterDataDelta(branchId)
        }
    }

    fun toggleConnectionMode() {
        val next = if (connectionStatus.value == ConnectionStatus.ONLINE) ConnectionStatus.OFFLINE else ConnectionStatus.ONLINE
        syncManager.setConnectionStatus(next)
        if (next == ConnectionStatus.ONLINE) {
            viewModelScope.launch {
                syncManager.pushPendingEventsBatch()
            }
        }
    }
}
