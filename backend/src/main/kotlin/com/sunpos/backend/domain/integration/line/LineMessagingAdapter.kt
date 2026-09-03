package com.sunpos.backend.domain.integration.line

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class LineMessagingAdapter(
    private val notificationLogRepository: NotificationLogRepository
) : MessagingGateway {

    private val logger = LoggerFactory.getLogger(LineMessagingAdapter::class.java)

    // Controlled mock property for test simulation
    var simulateApiFailure: Boolean = false

    @Transactional
    override fun sendNotification(
        recipientId: String,
        template: NotificationTemplate,
        data: Map<String, Any>
    ): NotificationLog {
        val payloadStr = data.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":\"${it.value}\"" }

        val log = NotificationLog(
            recipientId = recipientId,
            channel = "LINE",
            templateType = template,
            payloadJson = payloadStr,
            status = NotificationStatus.PENDING
        )
        val savedLog = notificationLogRepository.save(log)

        try {
            if (simulateApiFailure) {
                throw RuntimeException("LINE Platform Messaging API Connection Timeout (HTTP 504)")
            }

            // Simulate Successful API Dispatch to LINE Platform
            logger.info("Successfully dispatched LINE notification to recipient $recipientId [Template: $template]")
            savedLog.status = NotificationStatus.DELIVERED
            savedLog.sentAt = Instant.now()
        } catch (ex: Exception) {
            // Fault-Tolerant Circuit Breaking: Catch external LINE API failures cleanly so POS transactions NEVER roll back!
            logger.warn("LINE Messaging API failure for recipient $recipientId. Enqueued for retry: ${ex.message}")
            savedLog.status = NotificationStatus.FAILED
            savedLog.errorMessage = ex.message
            savedLog.retryCount = 1
        }

        return notificationLogRepository.save(savedLog)
    }

    @Transactional
    fun retryFailedNotifications(): Int {
        val failedLogs = notificationLogRepository.findByStatus(NotificationStatus.FAILED)
        var retriedCount = 0
        for (log in failedLogs) {
            try {
                log.status = NotificationStatus.RETRYING
                log.retryCount = log.retryCount + 1
                // Mock retry success
                log.status = NotificationStatus.DELIVERED
                log.sentAt = Instant.now()
                log.errorMessage = null
                notificationLogRepository.save(log)
                retriedCount++
            } catch (ex: Exception) {
                log.status = NotificationStatus.FAILED
                log.errorMessage = ex.message
                notificationLogRepository.save(log)
            }
        }
        return retriedCount
    }
}
