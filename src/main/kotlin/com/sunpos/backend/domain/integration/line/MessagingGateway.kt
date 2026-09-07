package com.sunpos.backend.domain.integration.line

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

enum class NotificationTemplate {
    ORDER_CONFIRMED,
    KITCHEN_PREPARING,
    ORDER_READY,
    ORDER_COMPLETED,
    COUPON_ISSUED
}

enum class NotificationStatus {
    PENDING,
    DELIVERED,
    FAILED,
    RETRYING
}

class NotificationLog(
    val id: String = UUID.randomUUID().toString(),
    var recipientId: String = "",
    var channel: String = "LINE",
    var templateType: NotificationTemplate = NotificationTemplate.ORDER_CONFIRMED,
    var payloadJson: String = "",
    var status: NotificationStatus = NotificationStatus.PENDING,
    var retryCount: Int = 0,
    var errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    var sentAt: Instant? = null
)

@Repository
class NotificationLogRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<NotificationLog>(jdbcTemplate, "notification_logs", NotificationLog::class.java) {
    fun findByRecipientIdOrderByCreatedAtDesc(recipientId: String): List<NotificationLog> =
        findByField("recipientId", recipientId).sortedByDescending { it.createdAt }
    fun findByStatus(status: NotificationStatus): List<NotificationLog> =
        findByField("status", status.name)
}

// Interface Abstraction
interface MessagingGateway {
    fun sendNotification(recipientId: String, template: NotificationTemplate, data: Map<String, Any>): NotificationLog
}
