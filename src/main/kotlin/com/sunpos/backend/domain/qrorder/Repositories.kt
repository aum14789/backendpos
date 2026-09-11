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

    fun findTimedOutOrders(cutoff: java.time.Instant): List<QrOrder> {
        val sql = "SELECT * FROM qr_orders WHERE status IN ('pending', 'sent_to_branch') AND created_at < ?"
        return jdbcTemplate.query(sql, rowMapper, java.sql.Timestamp.from(cutoff))
    }
}

@Repository
class QuarantinedQrOrderRepository(jdbcTemplate: JdbcTemplate) :
    JdbcRepository<QuarantinedQrOrder>(jdbcTemplate, "quarantined_qr_orders", QuarantinedQrOrder::class.java) {

    fun findByBranchId(branchId: String): List<QuarantinedQrOrder> {
        return findByField("branchId", branchId)
    }

    fun findByBranchIdAndDateRange(branchId: String, from: java.time.Instant, to: java.time.Instant): List<QuarantinedQrOrder> {
        val sql = "SELECT * FROM quarantined_qr_orders WHERE branch_id = ? AND quarantined_at >= ? AND quarantined_at <= ? ORDER BY quarantined_at DESC"
        return jdbcTemplate.query(sql, rowMapper, branchId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to))
    }
}

@Repository
class QrOrderItemRepository(jdbcTemplate: JdbcTemplate) :
    JdbcRepository<QrOrderItem>(jdbcTemplate, "qr_order_items", QrOrderItem::class.java) {

    fun findByOrderId(orderId: String): List<QrOrderItem> {
        return findByField("orderId", orderId)
    }
}

/**
 * Branch-level QR menu switches.  These are deliberately separate from a
 * menu item's normal availability: a branch may hide an item from QR ordering
 * while staff can still sell it from the POS (or another branch can sell it).
 */
@Repository
class QrOrderMenuItemSettingRepository(jdbcTemplate: JdbcTemplate) :
    JdbcRepository<QrOrderMenuItemSetting>(jdbcTemplate, "qr_order_menu_item_settings", QrOrderMenuItemSetting::class.java) {
    fun findByBranchId(branchId: String): List<QrOrderMenuItemSetting> =
        findByField("branchId", branchId)

    fun findByBranchIdAndMenuItemId(branchId: String, menuItemId: String): java.util.Optional<QrOrderMenuItemSetting> =
        findByBranchId(branchId).firstOrNull { it.menuItemId == menuItemId }.let { java.util.Optional.ofNullable(it) }
}
