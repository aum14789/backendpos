package com.sunpos.backend.domain.qrorder

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class QrOrderRepository(jdbcTemplate: JdbcTemplate) :
    JdbcRepository<QrOrder>(jdbcTemplate, "qr_orders", QrOrder::class.java) {

    fun findByBranchIdAndStatus(branchId: String, status: QrOrderStatus): List<QrOrder> {
        return findByFields(mapOf("branchId" to branchId, "status" to status))
    }

    fun findByBranchIdAndTableNumber(branchId: String, tableNumber: String): List<QrOrder> {
        return findByFields(mapOf("branchId" to branchId, "tableNumber" to tableNumber))
    }

    fun findByIdempotencyKey(key: String): java.util.Optional<QrOrder> {
        return findOneByField("idempotencyKey", key)
    }
}

@Repository
class QrOrderItemRepository(jdbcTemplate: JdbcTemplate) :
    JdbcRepository<QrOrderItem>(jdbcTemplate, "qr_order_items", QrOrderItem::class.java) {

    fun findByOrderId(orderId: String): List<QrOrderItem> {
        return findByField("orderId", orderId)
    }
}
