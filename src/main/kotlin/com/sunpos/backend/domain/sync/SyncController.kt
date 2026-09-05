package com.sunpos.backend.domain.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.domain.businessday.BusinessDayRepository
import com.sunpos.backend.domain.businessday.BusinessDayStatus
import com.sunpos.backend.domain.catalog.MenuItem
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.catalog.MenuCategory
import com.sunpos.backend.domain.catalog.MenuCategoryRepository
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.payment.*
import com.sunpos.backend.domain.shift.*
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class SyncEvent(
    val eventId: String = "",
    var aggregateType: String = "",
    var aggregateId: String = "",
    var eventType: String = "",
    var deviceId: String = "",
    var branchId: String = "",
    var payload: String = "",
    val processedAt: Instant = Instant.now(),
    val createdAt: Instant = Instant.now()
) {
    val id: String get() = eventId
}

@Repository
class SyncEventRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<SyncEvent>(jdbcTemplate, "sync_events", SyncEvent::class.java)

class DeviceSyncState(
    val deviceId: String = "",
    var branchId: String = "",
    var deviceName: String = "",
    var appVersion: String = "",
    var ipAddress: String? = null,
    var syncStatus: String = "SYNCED",
    var pendingOutboxCount: Int = 0,
    var lastSyncedAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
) {
    val id: String get() = deviceId
}

@Repository
class DeviceSyncStateRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<DeviceSyncState>(jdbcTemplate, "device_sync_states", DeviceSyncState::class.java)

data class SyncPushRequest(
    val events: List<SyncEventDto> = emptyList(),
    val items: List<SyncQueueItemDto> = emptyList(),
    val branchId: String = "",
    val deviceId: String = ""
)

data class SyncQueueItemDto(
    val queueId: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val action: String = "",
    val payloadJson: String = "",
    val createdAt: Long = 0L
)

data class SyncEventDto(
    val eventId: String = "",
    val aggregateType: String = "",
    val aggregateId: String = "",
    val eventType: String = "",
    val deviceId: String = "",
    val branchId: String = "",
    val payload: String = "",
    val createdAt: Instant = Instant.now()
)

data class SyncPushResult(
    val processedEventIds: List<String>,
    val duplicateEventIds: List<String>,
    val successIds: List<String> = processedEventIds
)

data class SyncBranchDto(
    val branchId: String = "",
    val companyId: String = "",
    val name: String = "",
    val code: String = "",
    val businessDayCloseTime: String = "02:00",
    val taxRate: Double = 7.0,
    val serviceChargeRate: Double = 0.0
)

data class SyncCategoryDto(
    val categoryId: String = "",
    val branchId: String = "",
    val name: String = "",
    val description: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

data class SyncMenuItemDto(
    val itemId: String = "",
    val branchId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val basePrice: Long = 0L, // In Satang
    val availability: String = "AVAILABLE",
    val imageUrl: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val allowDecimal: Boolean = false,
    val unitName: String? = null
)

data class SyncBuffetTierDto(
    val tierId: String = "",
    val promotionId: String = "",
    val name: String = "",
    val adultPrice: Long = 0L, // In Satang
    val childPrice: Long = 0L, // In Satang
    val timeLimitMinutes: Int = 90,
    val brandId: String? = null,
    val branchId: String? = null,
    val isActive: Boolean = true,
    val eligibleItemIds: List<String> = emptyList()
)

data class SyncZoneDto(
    val zoneId: String = "",
    val branchId: String = "",
    val name: String = "",
    val zoneType: String = "DINE_IN",
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

data class SyncTableDto(
    val tableId: String = "",
    val branchId: String = "",
    val zoneId: String? = null,
    val tableTypeId: String? = null,
    val nameNumber: String = "",
    val capacity: Int = 4,
    val status: String = "AVAILABLE",
    val isActive: Boolean = true
)

data class SyncPromotionDto(
    val promotionId: String = "",
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val promoType: String = "PERCENTAGE",
    val priority: Int = 0,
    val isActive: Boolean = true,
    val discountRate: Double = 0.0,
    val discountAmount: Long = 0L, // In Satang
    val minOrderAmount: Long = 0L, // In Satang
    val stackingPolicy: String = "STACKABLE"
)

data class SyncUserDto(
    val userId: String = "",
    val companyId: String = "",
    val username: String = "",
    val fullName: String = "",
    val pinHash: String = "1234",
    val isActive: Boolean = true,
    val permissions: List<String> = emptyList()
)

data class SyncDeltaResponse(
    val branchId: String = "",
    val sinceTimestamp: Instant = Instant.EPOCH,
    val branch: SyncBranchDto? = null,
    val menuItems: List<SyncMenuItemDto> = emptyList(),
    val categories: List<SyncCategoryDto> = emptyList(),
    val buffetTiers: List<SyncBuffetTierDto> = emptyList(),
    val zones: List<SyncZoneDto> = emptyList(),
    val tables: List<SyncTableDto> = emptyList(),
    val promotions: List<SyncPromotionDto> = emptyList(),
    val users: List<SyncUserDto> = emptyList(),
    val deviceCapabilities: List<String> = emptyList(),
    val crmPolicy: CrmPolicyDto = CrmPolicyDto(),
    val serverTime: Instant = Instant.now()
)

/**
 * CRM policy metadata published to Android POS.
 * Android enforces these rules locally based on connectivity state.
 */
data class CrmPolicyDto(
    val earnPointsOffline: Boolean = true,
    val redeemPointsOffline: Boolean = false,
    val useCouponOffline: Boolean = false
)

@Service
class SyncService(
    private val syncEventRepository: SyncEventRepository,
    private val deviceSyncStateRepository: DeviceSyncStateRepository,
    private val menuItemRepository: MenuItemRepository,
    private val categoryRepository: MenuCategoryRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentTransactionRepository,
    private val shiftRepository: CashierShiftRepository,
    private val businessDayRepository: BusinessDayRepository,
    private val objectMapper: ObjectMapper,
    private val crmService: com.sunpos.backend.domain.crm.CrmService? = null,
    private val couponService: com.sunpos.backend.domain.promotion.CouponService? = null,
    private val deviceCapabilityRepository: com.sunpos.backend.domain.organization.DeviceCapabilityRepository? = null,
    private val branchRepository: com.sunpos.backend.domain.organization.BranchRepository? = null,
    private val zoneRepository: com.sunpos.backend.domain.table.ZoneRepository? = null,
    private val tableRepository: com.sunpos.backend.domain.table.TableRepository? = null,
    private val buffetPromotionTierRepository: com.sunpos.backend.domain.order.BuffetPromotionTierRepository? = null,
    private val buffetTierMenuItemRepository: com.sunpos.backend.domain.order.BuffetTierMenuItemRepository? = null,
    private val buffetPromotionRepository: com.sunpos.backend.domain.order.BuffetPromotionRepository? = null,
    private val promotionRepository: com.sunpos.backend.domain.promotion.PromotionRepository? = null,
    private val userRepository: com.sunpos.backend.domain.identity.UserRepository? = null,
    private val userRoleRepository: com.sunpos.backend.domain.identity.UserRoleRepository? = null,
    private val rolePermissionRepository: com.sunpos.backend.domain.identity.RolePermissionRepository? = null,
    private val permissionRepository: com.sunpos.backend.domain.identity.PermissionRepository? = null
) {
    private val log = LoggerFactory.getLogger(SyncService::class.java)

    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    /**
     * Idempotent Event Apply Processor:
     * Guarantees:
     *   1. If eventId is already processed -> return duplicate acknowledgement without re-applying.
     *   2. Applies domain side-effects for ORDER_CREATED, ORDER_UPDATED, ORDER_COMPLETED,
     *      PAYMENT_COMPLETED, SHIFT_OPENED, SHIFT_CLOSED.
     *   3. Saves sync_events audit record and updates device_sync_states.
     */
    @Transactional
    fun processPush(request: SyncPushRequest): SyncPushResult {
        val processed = mutableListOf<String>()
        val duplicates = mutableListOf<String>()

        val eventsToProcess: List<SyncEventDto> = if (request.events.isNotEmpty()) {
            request.events
        } else {
            request.items.map { it ->
                SyncEventDto(
                    eventId = it.queueId.ifBlank { "sq-${System.currentTimeMillis()}-${it.entityId}" },
                    aggregateType = it.entityType,
                    aggregateId = it.entityId,
                    eventType = when (it.entityType) {
                        "ORDER" -> "ORDER_CREATED"
                        "PAYMENT" -> "PAYMENT_COMPLETED"
                        "ORDER_STATUS" -> "ORDER_COMPLETED"
                        "TABLE_STATUS" -> "TABLE_STATUS_UPDATED"
                        else -> "${it.entityType}_${it.action}"
                    },
                    deviceId = request.deviceId.ifBlank { "pos-edge-01" },
                    branchId = request.branchId.ifBlank { "branch-001" },
                    payload = it.payloadJson,
                    createdAt = if (it.createdAt > 0) Instant.ofEpochMilli(it.createdAt) else Instant.now()
                )
            }
        }

        for (dto in eventsToProcess) {
            // Strict Idempotency Check: Reject duplicate event UUIDs
            if (syncEventRepository.existsById(dto.eventId)) {
                duplicates.add(dto.eventId)
                continue
            }

            try {
                // Apply domain side-effects according to eventType
                applyDomainEvent(dto)

                // Persist event in sync_events audit log
                val entity = SyncEvent(
                    eventId = dto.eventId,
                    aggregateType = dto.aggregateType,
                    aggregateId = dto.aggregateId,
                    eventType = dto.eventType,
                    deviceId = dto.deviceId,
                    branchId = dto.branchId,
                    payload = dto.payload,
                    createdAt = dto.createdAt
                )
                syncEventRepository.save(entity)
                processed.add(dto.eventId)

                // Update Device Health & Sync Status
                val deviceState = deviceSyncStateRepository.findById(dto.deviceId).orElseGet {
                    DeviceSyncState(
                        deviceId = dto.deviceId,
                        branchId = dto.branchId,
                        deviceName = "POS Terminal (${dto.deviceId})",
                        appVersion = "v1.10.0"
                    )
                }
                deviceState.lastSyncedAt = Instant.now()
                deviceState.syncStatus = "SYNCED"
                deviceState.pendingOutboxCount = 0
                deviceState.updatedAt = Instant.now()
                deviceSyncStateRepository.save(deviceState)

            } catch (e: Exception) {
                log.error("Failed to apply event ${dto.eventId} (${dto.eventType}): ${e.message}", e)
                throw e
            }
        }

        return SyncPushResult(processedEventIds = processed, duplicateEventIds = duplicates)
    }

    private fun applyDomainEvent(dto: SyncEventDto) {
        val payloadMap: Map<String, Any?> = try {
            objectMapper.readValue(dto.payload)
        } catch (e: Exception) {
            emptyMap()
        }

        when (dto.eventType) {
            "ORDER_CREATED" -> {
                val orderId = dto.aggregateId
                if (!orderRepository.existsById(orderId)) {
                    val orderNumber = (payloadMap["orderNumber"] as? String) ?: "ORD-SYNC-${System.currentTimeMillis() % 10000}"
                    val orderTypeStr = (payloadMap["orderType"] as? String) ?: "DINE_IN"
                    val orderType = try { OrderType.valueOf(orderTypeStr) } catch (_: Exception) { OrderType.DINE_IN }
                    val totalSatang = (payloadMap["totalSatang"] as? Number)?.toLong() ?: 0L
                    val grossSatang = (payloadMap["grossSatang"] as? Number)?.toLong() ?: totalSatang
                    val discountSatang = (payloadMap["discountSatang"] as? Number)?.toLong() ?: 0L
                    val taxSatang = (payloadMap["taxSatang"] as? Number)?.toLong() ?: 0L

                    // Resolve open business day or fallback
                    val openDayId = businessDayRepository.findByBranchIdAndStatus(dto.branchId, BusinessDayStatus.OPEN)
                        .firstOrNull()?.id ?: "bday-${dto.branchId}"

                    val order = Order(
                        id = orderId,
                        branchId = dto.branchId,
                        orderNumber = orderNumber,
                        orderType = orderType,
                        channel = OrderChannel.POS,
                        status = OrderStatus.OPEN,
                        financialStatus = FinancialStatus.UNPAID,
                        kitchenStatus = KitchenStatus.NOT_SENT,
                        subtotalAmount = BigDecimal(grossSatang).divide(BigDecimal(100), SCALE, ROUNDING),
                        discountAmount = BigDecimal(discountSatang).divide(BigDecimal(100), SCALE, ROUNDING),
                        taxAmount = BigDecimal(taxSatang).divide(BigDecimal(100), SCALE, ROUNDING),
                        totalAmount = BigDecimal(totalSatang).divide(BigDecimal(100), SCALE, ROUNDING),
                        businessDayId = openDayId,
                        createdBy = "sync-pos"
                    )
                    orderRepository.save(order)
                }
            }

            "ORDER_UPDATED" -> {
                val orderOpt = orderRepository.findById(dto.aggregateId)
                if (orderOpt.isPresent) {
                    val order = orderOpt.get()
                    val totalSatang = (payloadMap["totalSatang"] as? Number)?.toLong()
                    if (totalSatang != null) {
                        order.totalAmount = BigDecimal(totalSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                    }
                    val manualDiscountSatang = (payloadMap["manualDiscountSatang"] as? Number)?.toLong()
                    if (manualDiscountSatang != null) {
                        order.discountAmount = BigDecimal(manualDiscountSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                    }
                    order.updatedAt = Instant.now()
                    orderRepository.save(order)
                }
            }

            "ORDER_COMPLETED" -> {
                val orderOpt = orderRepository.findById(dto.aggregateId)
                if (orderOpt.isPresent) {
                    val order = orderOpt.get()
                    order.status = OrderStatus.COMPLETED
                    order.financialStatus = FinancialStatus.PAID
                    order.updatedAt = Instant.now()
                    orderRepository.save(order)
                }
            }

            "PAYMENT_COMPLETED" -> {
                val paymentId = dto.aggregateId
                if (!paymentRepository.existsById(paymentId)) {
                    val orderId = (payloadMap["orderId"] as? String) ?: ""
                    val methodStr = (payloadMap["method"] as? String) ?: "CASH"
                    val method = try { PaymentMethod.valueOf(methodStr) } catch (_: Exception) { PaymentMethod.CASH }
                    val amountSatang = (payloadMap["amountSatang"] as? Number)?.toLong() ?: 0L
                    val tenderedSatang = (payloadMap["tenderedSatang"] as? Number)?.toLong() ?: amountSatang
                    val changeSatang = (payloadMap["changeSatang"] as? Number)?.toLong() ?: 0L
                    val createdBy = (payloadMap["createdBy"] as? String) ?: "cashier"

                    val amount = BigDecimal(amountSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                    val tendered = BigDecimal(tenderedSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                    val change = BigDecimal(changeSatang).divide(BigDecimal(100), SCALE, ROUNDING)

                    val payment = PaymentTransaction(
                        id = paymentId,
                        orderId = orderId,
                        branchId = dto.branchId,
                        deviceId = dto.deviceId,
                        paymentMethod = method,
                        amount = amount,
                        tenderedAmount = tendered,
                        changeAmount = change,
                        status = PaymentStatus.SUCCESS,
                        idempotencyKey = paymentId,
                        createdBy = createdBy
                    )
                    paymentRepository.save(payment)

                    // If order exists, ensure order is marked COMPLETED
                    if (orderId.isNotBlank()) {
                        val orderOpt = orderRepository.findById(orderId)
                        if (orderOpt.isPresent) {
                            val order = orderOpt.get()
                            order.status = OrderStatus.COMPLETED
                            order.financialStatus = FinancialStatus.PAID
                            orderRepository.save(order)
                        }
                    }
                }
            }

            "SHIFT_OPENED" -> {
                val shiftId = dto.aggregateId
                if (!shiftRepository.existsById(shiftId)) {
                    val userId = (payloadMap["userId"] as? String) ?: "cashier"
                    val openingCashSatang = (payloadMap["openingCashSatang"] as? Number)?.toLong() ?: 0L
                    val openingCash = BigDecimal(openingCashSatang).divide(BigDecimal(100), SCALE, ROUNDING)

                    val shift = CashierShift(
                        id = shiftId,
                        branchId = dto.branchId,
                        deviceId = dto.deviceId,
                        userId = userId,
                        status = ShiftStatus.OPEN,
                        openingCash = openingCash,
                        expectedCash = openingCash
                    )
                    shiftRepository.save(shift)
                }
            }

            "SHIFT_CLOSED" -> {
                val shiftOpt = shiftRepository.findById(dto.aggregateId)
                if (shiftOpt.isPresent) {
                    val shift = shiftOpt.get()
                    val actualSatang = (payloadMap["actualCashSatang"] as? Number)?.toLong() ?: 0L
                    val varianceSatang = (payloadMap["varianceSatang"] as? Number)?.toLong() ?: 0L
                    val varianceTypeStr = (payloadMap["varianceType"] as? String) ?: "ZERO"

                    shift.actualCash = BigDecimal(actualSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                    shift.variance = BigDecimal(varianceSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                    shift.varianceType = try { VarianceType.valueOf(varianceTypeStr) } catch (_: Exception) { VarianceType.ZERO }
                    shift.status = ShiftStatus.CLOSED
                    shift.closedAt = Instant.now()
                    shiftRepository.save(shift)
                }
            }

            "CUSTOMER_CREATED", "CUSTOMER_UPSERTED" -> {
                val phone = payloadMap["phone"] as? String
                val displayName = (payloadMap["displayName"] as? String) ?: (payloadMap["name"] as? String) ?: "Customer"
                val customerGroup = (payloadMap["customerGroup"] as? String) ?: "GENERAL"

                if (phone != null && phone.isNotBlank()) {
                    crmService?.createCustomer(
                        com.sunpos.backend.domain.crm.CreateCustomerDto(
                            displayName = displayName,
                            phone = phone,
                            customerGroup = customerGroup,
                            primaryBranchId = dto.branchId
                        )
                    )
                }
            }

            "ORDER_CUSTOMER_LINKED" -> {
                val orderId = (payloadMap["orderId"] as? String) ?: dto.aggregateId
                val customerId = payloadMap["customerId"] as? String
                val orderOpt = orderRepository.findById(orderId)
                if (orderOpt.isPresent) {
                    val order = orderOpt.get()
                    order.customerId = customerId
                    order.updatedAt = Instant.now()
                    orderRepository.save(order)
                }
            }

            "POINT_EARN", "POINT_EARNED" -> {
                val customerId = (payloadMap["customerId"] as? String) ?: dto.aggregateId
                val orderId = (payloadMap["orderId"] as? String) ?: (payloadMap["referenceId"] as? String) ?: dto.aggregateId
                val orderAmountSatang = (payloadMap["orderAmountSatang"] as? Number)?.toLong()
                val orderAmount = if (orderAmountSatang != null) {
                    BigDecimal(orderAmountSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                } else {
                    BigDecimal((payloadMap["orderAmount"] as? Number)?.toDouble() ?: 0.0).setScale(SCALE, ROUNDING)
                }
                if (customerId.isNotBlank() && orderId.isNotBlank() && orderAmount > BigDecimal.ZERO) {
                    crmService?.earnPointsForOrder(customerId, orderId, orderAmount)
                }
            }

            "POINT_REDEEM", "POINT_REDEEMED" -> {
                val customerId = (payloadMap["customerId"] as? String) ?: dto.aggregateId
                val orderId = (payloadMap["orderId"] as? String) ?: (payloadMap["referenceId"] as? String) ?: dto.aggregateId
                val points = BigDecimal((payloadMap["pointsRedeemed"] as? Number ?: payloadMap["points"] as? Number)?.toDouble() ?: 0.0).setScale(SCALE, ROUNDING)
                val notes = payloadMap["notes"] as? String
                if (customerId.isNotBlank() && points > BigDecimal.ZERO) {
                    crmService?.redeemPoints(
                        customerId = customerId,
                        pointsToRedeem = points,
                        orderId = orderId,
                        notes = notes
                    )
                }
            }

            "POINT_REVERSE", "POINT_REVERSED" -> {
                val customerId = (payloadMap["customerId"] as? String) ?: dto.aggregateId
                val orderId = (payloadMap["orderId"] as? String) ?: (payloadMap["referenceId"] as? String) ?: dto.aggregateId
                if (customerId.isNotBlank() && orderId.isNotBlank()) {
                    crmService?.reversePoints(orderId, customerId)
                }
            }

            "COUPON_REDEEM", "COUPON_REDEEMED" -> {
                val couponCode = (payloadMap["code"] as? String) ?: (payloadMap["couponCode"] as? String) ?: dto.aggregateId
                val orderId = (payloadMap["orderId"] as? String) ?: dto.aggregateId
                val customerId = payloadMap["customerId"] as? String
                val orderAmountSatang = (payloadMap["orderAmountSatang"] as? Number)?.toLong()
                val orderAmount = if (orderAmountSatang != null) {
                    BigDecimal(orderAmountSatang).divide(BigDecimal(100), SCALE, ROUNDING)
                } else {
                    BigDecimal((payloadMap["orderAmount"] as? Number)?.toDouble() ?: 0.0).setScale(SCALE, ROUNDING)
                }

                if (couponCode.isNotBlank() && orderId.isNotBlank()) {
                    try {
                        couponService?.redeemCoupon(
                            com.sunpos.backend.domain.promotion.RedeemCouponRequestDto(
                                code = couponCode,
                                orderId = orderId,
                                orderAmount = orderAmount,
                                customerId = customerId,
                                branchId = dto.branchId
                            )
                        )
                    } catch (ex: Exception) {
                        log.warn("COUPON_REDEEM sync handler skipped/failed for orderId=$orderId, code=$couponCode: ${ex.message}")
                    }
                }
            }
        }
    }

    fun getDelta(branchId: String, sinceTimestamp: Instant?, deviceId: String? = null): SyncDeltaResponse {
        val since = sinceTimestamp ?: Instant.EPOCH

        // 1. Branch details
        val branchInfo = branchRepository?.findById(branchId)?.orElse(null)?.let { b ->
            SyncBranchDto(
                branchId = b.id,
                companyId = b.companyId,
                name = b.name,
                code = b.code,
                businessDayCloseTime = b.businessDayCloseTime,
                taxRate = b.taxRate.toDouble(),
                serviceChargeRate = b.serviceChargeRate.toDouble()
            )
        }

        // 2. Menu Categories
        val rawCategories = categoryRepository.findByBranchIdOrderBySortOrderAsc(branchId)
        val categories = rawCategories.map { c ->
            SyncCategoryDto(
                categoryId = c.id,
                branchId = c.branchId.ifBlank { branchId },
                name = c.name,
                description = c.description,
                sortOrder = c.sortOrder,
                isActive = c.isActive
            )
        }

        // 3. Menu Items
        val rawMenuItems = menuItemRepository.findByBranchId(branchId)
        val menuItems = rawMenuItems.map { m ->
            SyncMenuItemDto(
                itemId = m.id,
                branchId = m.branchId.ifBlank { branchId },
                categoryId = m.categoryId,
                name = m.name,
                description = m.description,
                sku = m.sku,
                basePrice = m.basePrice.multiply(BigDecimal("100")).toLong(),
                availability = m.availability,
                imageUrl = m.imageUrl,
                sortOrder = m.sortOrder,
                isActive = m.isActive,
                allowDecimal = false,
                unitName = null
            )
        }

        // 4. Buffet Tiers
        val buffetTiers = mutableListOf<SyncBuffetTierDto>()
        if (buffetPromotionTierRepository != null) {
            val rawTiers = buffetPromotionTierRepository.findByBranchIdAndIsActiveTrue(branchId).ifEmpty {
                val b = branchRepository?.findById(branchId)?.orElse(null)
                val targetBrandId = b?.brandId
                if (!targetBrandId.isNullOrBlank()) {
                    buffetPromotionTierRepository.findByBrandIdAndIsActiveTrue(targetBrandId)
                } else {
                    emptyList()
                }
            }
            buffetTiers.addAll(rawTiers.map { bt ->
                val eligibleItemIds = buffetTierMenuItemRepository?.findMenuItemIdsByTierId(bt.id) ?: emptyList()
                SyncBuffetTierDto(
                    tierId = bt.id,
                    promotionId = bt.promotionId,
                    name = bt.name,
                    adultPrice = bt.adultPrice.multiply(BigDecimal("100")).toLong(),
                    childPrice = bt.childPrice.multiply(BigDecimal("100")).toLong(),
                    timeLimitMinutes = bt.timeLimitMinutes,
                    brandId = bt.brandId,
                    branchId = bt.branchId,
                    isActive = bt.isActive,
                    eligibleItemIds = eligibleItemIds
                )
            })
        }
        if (buffetTiers.isEmpty() && buffetPromotionRepository != null) {
            val b = branchRepository?.findById(branchId)?.orElse(null)
            val targetBrandId = b?.brandId ?: ""
            val promos = buffetPromotionRepository.findPromotionsForBranch(targetBrandId, branchId, com.sunpos.backend.domain.order.BuffetPromotionStatus.ACTIVE)
            buffetTiers.addAll(promos.map { p ->
                SyncBuffetTierDto(
                    tierId = p.id,
                    promotionId = p.id,
                    name = p.name,
                    adultPrice = p.pricePerPerson.multiply(BigDecimal("100")).toLong(),
                    childPrice = p.pricePerPerson.multiply(BigDecimal("50")).toLong(),
                    timeLimitMinutes = p.durationMinutes,
                    brandId = p.brandId,
                    branchId = p.branchId,
                    isActive = p.status == com.sunpos.backend.domain.order.BuffetPromotionStatus.ACTIVE
                )
            })
        }

        // 5. Zones
        val zones = if (zoneRepository != null) {
            zoneRepository.findByBranchIdOrderBySortOrderAsc(branchId).map { z ->
                SyncZoneDto(
                    zoneId = z.id,
                    branchId = z.branchId.ifBlank { branchId },
                    name = z.name,
                    zoneType = z.zoneType,
                    sortOrder = z.sortOrder,
                    isActive = true
                )
            }
        } else emptyList()

        // 6. Tables
        val tables = if (tableRepository != null) {
            tableRepository.findByBranchId(branchId).map { t ->
                SyncTableDto(
                    tableId = t.id,
                    branchId = t.branchId.ifBlank { branchId },
                    zoneId = t.zoneId,
                    tableTypeId = t.tableTypeId,
                    nameNumber = t.nameNumber,
                    capacity = t.capacity,
                    status = t.status,
                    isActive = true
                )
            }
        } else emptyList()

        // 7. Promotions
        val promotions = if (promotionRepository != null) {
            promotionRepository.findAll().filter { it.isActive }.map { p ->
                SyncPromotionDto(
                    promotionId = p.id,
                    code = p.code,
                    name = p.name,
                    description = p.description,
                    promoType = p.promoType.name,
                    priority = p.priority,
                    isActive = p.isActive,
                    discountRate = p.discountRate.toDouble(),
                    discountAmount = p.discountAmount.multiply(BigDecimal("100")).toLong(),
                    minOrderAmount = p.minAmount.multiply(BigDecimal("100")).toLong(),
                    stackingPolicy = p.stackingPolicy.name
                )
            }
        } else emptyList()

        // 8. Users & Permissions
        val users = if (userRepository != null) {
            val allUsers = userRepository.findByIsActiveTrueAndPinCodeIsNotNull().ifEmpty { userRepository.findAll() }
            val permCodeMap = if (permissionRepository != null) permissionRepository.findAll().associate { it.id to it.code } else emptyMap()
            val rolePerms = if (rolePermissionRepository != null) rolePermissionRepository.findAll() else emptyList()
            val userRoles = if (userRoleRepository != null) userRoleRepository.findAll() else emptyList()

            allUsers.map { u ->
                val userRoleIds = userRoles.filter { it.userId == u.id }.map { it.roleId }.toSet()
                val userPermIds = rolePerms.filter { userRoleIds.contains(it.roleId) }.map { it.permissionId }.toSet()
                val permCodes = userPermIds.mapNotNull { permCodeMap[it] }.ifEmpty {
                    listOf("ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL", "DISCOUNT_APPLY", "PAYMENT_MANAGE", "SHIFT_MANAGE")
                }

                SyncUserDto(
                    userId = u.id,
                    companyId = u.companyId.ifBlank { "comp-001" },
                    username = u.username,
                    fullName = u.fullName,
                    pinHash = u.pinCode ?: "1234",
                    isActive = u.isActive,
                    permissions = permCodes
                )
            }
        } else emptyList()

        // 9. Include device capabilities if deviceId is provided
        val capabilities: List<String> = if (deviceId != null && deviceCapabilityRepository != null) {
            deviceCapabilityRepository.findByDeviceIdAndIsActiveTrue(deviceId)
                .map { it.capability.name }
        } else {
            emptyList()
        }

        return SyncDeltaResponse(
            branchId = branchId,
            sinceTimestamp = since,
            branch = branchInfo,
            menuItems = menuItems,
            categories = categories,
            buffetTiers = buffetTiers,
            zones = zones,
            tables = tables,
            promotions = promotions,
            users = users,
            deviceCapabilities = capabilities,
            crmPolicy = CrmPolicyDto(),
            serverTime = Instant.now()
        )
    }

    fun listDeviceStates(): List<DeviceSyncState> {
        return deviceSyncStateRepository.findAll().sortedByDescending { it.lastSyncedAt }
    }
}

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val syncService: SyncService,
    private val seeder: com.sunpos.backend.config.DataSeeder? = null
) {
    @PostMapping("/push")
    fun pushEvents(@RequestBody request: SyncPushRequest): ApiResponse<SyncPushResult> {
        val result = syncService.processPush(request)
        return ApiResponse.success(result, "Sync batch processed successfully")
    }

    @GetMapping("/pull")
    fun pullDelta(
        @RequestParam branchId: String,
        @RequestParam(required = false) since: String?,
        @RequestParam(required = false) sinceTimestamp: String?,
        @RequestParam(required = false) deviceId: String?
    ): ApiResponse<SyncDeltaResponse> {
        val rawTime = since ?: sinceTimestamp
        val parsedInstant = try {
            if (rawTime.isNullOrBlank() || rawTime == "0") {
                Instant.EPOCH
            } else if (rawTime.toLongOrNull() != null) {
                Instant.ofEpochMilli(rawTime.toLong())
            } else {
                Instant.parse(rawTime)
            }
        } catch (_: Exception) {
            Instant.EPOCH
        }

        val delta = syncService.getDelta(branchId, parsedInstant, deviceId)
        return ApiResponse.success(delta, "Delta master data retrieved successfully")
    }

    @GetMapping("/devices")
    fun getDevices(): ApiResponse<List<DeviceSyncState>> {
        return ApiResponse.success(syncService.listDeviceStates(), "Device sync states retrieved successfully")
    }

    @GetMapping("/health")
    fun healthCheck(): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(
            mapOf(
                "status" to "UP",
                "service" to "sunpos-cloud-sync",
                "serverTime" to Instant.now()
            ),
            "Cloud Sync service is operational"
        )
    }

    @PostMapping("/seed")
    fun seedDatabase(@RequestParam(required = false, defaultValue = "false") force: Boolean): ApiResponse<String> {
        seeder?.seedMasterDataIfEmpty(force = force)
        return ApiResponse.success("Seeding completed", "Database seed requested")
    }
}
