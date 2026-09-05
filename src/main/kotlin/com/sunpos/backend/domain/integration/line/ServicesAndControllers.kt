package com.sunpos.backend.domain.integration.line

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.domain.crm.CrmService
import com.sunpos.backend.domain.crm.CustomerDetailsDto
import com.sunpos.backend.domain.crm.CustomerIdentity
import com.sunpos.backend.domain.crm.CustomerIdentityRepository
import com.sunpos.backend.domain.crm.CustomerRepository
import com.sunpos.backend.domain.crm.IdentityType
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ── DTOs ──

data class LinkLineRequestDto(
    val customerId: String,
    val lineUserId: String,
    val displayName: String? = null
)

data class UnlinkLineRequestDto(
    val customerId: String,
    val lineUserId: String? = null
)

data class CustomerSummaryDto(
    val customerId: String,
    val displayName: String,
    val tierName: String,
    val currentPoints: BigDecimal,
    val phone: String? = null
)

data class LineWebhookResponseDto(
    val success: Boolean,
    val message: String,
    val eventCount: Int,
    val matchedCustomers: List<CustomerSummaryDto> = emptyList()
)

data class LineConfigDto(
    val channelId: String?,
    val channelSecret: String?,
    val channelAccessToken: String?,
    val enabled: Boolean,
    val mockMode: Boolean
)

// ── Entity & Repository ──

class LineOaConfig(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String? = null,
    var channelId: String = "",
    var channelSecret: String = "",
    var channelAccessToken: String = "",
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

@Repository
class LineOaConfigRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<LineOaConfig>(jdbcTemplate, "line_oa_configs", LineOaConfig::class.java)

// ── Service ──

@Service
class LineIntegrationService(
    private val identityRepository: CustomerIdentityRepository,
    private val customerRepository: CustomerRepository,
    private val lineConfigRepository: LineOaConfigRepository,
    private val crmService: CrmService,
    private val messagingGateway: MessagingGateway,
    private val notificationLogRepository: NotificationLogRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${sunpos.line.enabled:false}") private val lineEnabledProp: Boolean = false,
    @Value("\${sunpos.line.channel-id:}") private val channelIdProp: String = "",
    @Value("\${sunpos.line.channel-secret:}") private val channelSecretProp: String = "",
    @Value("\${sunpos.line.channel-access-token:}") private val channelAccessTokenProp: String = "",
    @Value("\${sunpos.line.mock-mode:true}") private val mockModeProp: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(LineIntegrationService::class.java)

    @Transactional
    fun linkLineIdentity(customerId: String, lineUserId: String): CustomerIdentity {
        val trimmedLineId = lineUserId.trim()
        if (trimmedLineId.isBlank()) {
            throw IllegalArgumentException("LINE User ID must not be blank")
        }

        // Verify customer exists
        val customer = customerRepository.findById(customerId)
            .orElseThrow { NoSuchElementException("Customer '$customerId' not found") }

        // Prevent Duplicate Identities across different customers
        val existing = identityRepository.findByIdentityTypeAndIdentityValue(IdentityType.LINE, trimmedLineId)
        if (existing.isPresent) {
            val prev = existing.get()
            if (prev.customerId != customerId) {
                throw IllegalArgumentException("LINE User ID '$trimmedLineId' is already linked to another customer '${prev.customerId}'")
            }
            return prev
        }

        val identity = CustomerIdentity(
            companyId = customer.companyId,
            customerId = customerId,
            identityType = IdentityType.LINE,
            identityValue = trimmedLineId,
            isPrimary = false
        )
        val saved = identityRepository.save(identity)
        logger.info("Successfully linked LINE user ID '{}' to customer '{}'", trimmedLineId, customerId)
        return saved
    }

    @Transactional
    fun unlinkLineIdentity(customerId: String, lineUserId: String? = null): Boolean {
        val identities = identityRepository.findByCustomerId(customerId)
        val lineIdentities = identities.filter { it.identityType == IdentityType.LINE }
            .filter { lineUserId.isNullOrBlank() || it.identityValue == lineUserId.trim() }

        if (lineIdentities.isEmpty()) {
            logger.info("No LINE identity found to unlink for customer '{}'", customerId)
            return false
        }

        identityRepository.deleteAll(lineIdentities)
        logger.info("Successfully unlinked {} LINE identities for customer '{}'", lineIdentities.size, customerId)
        return true
    }

    fun findCustomerByLineUserId(lineUserId: String): CustomerDetailsDto? {
        val trimmed = lineUserId.trim()
        if (trimmed.isBlank()) return null

        val identityOpt = identityRepository.findByIdentityTypeAndIdentityValue(IdentityType.LINE, trimmed)
        if (identityOpt.isEmpty) return null

        val identity = identityOpt.get()
        return try {
            crmService.getCustomerDetails(identity.customerId, identity.companyId)
        } catch (ex: Exception) {
            logger.warn("Could not retrieve customer details for customer ID {}: {}", identity.customerId, ex.message)
            null
        }
    }

    fun processWebhook(payload: String, signature: String?): LineWebhookResponseDto {
        val config = getEffectiveConfig()
        
        // 1. Signature Validation (enforced when not mock mode and secret present)
        if (!config.mockMode && !config.channelSecret.isNullOrBlank()) {
            if (signature.isNullOrBlank() || !validateLineSignature(payload, signature, config.channelSecret)) {
                logger.warn("Invalid LINE webhook signature received")
                return LineWebhookResponseDto(
                    success = false,
                    message = "Invalid LINE webhook signature",
                    eventCount = 0
                )
            }
        }

        // 2. Parse Webhook JSON Events
        val matchedList = mutableListOf<CustomerSummaryDto>()
        var eventCount = 0

        try {
            if (payload.isNotBlank()) {
                val rootNode: JsonNode = objectMapper.readTree(payload)
                val eventsNode = rootNode.get("events")
                if (eventsNode != null && eventsNode.isArray) {
                    eventCount = eventsNode.size()
                    for (event in eventsNode) {
                        val userId = event.path("source").path("userId").asText(null)
                            ?: event.path("userId").asText(null)

                        if (!userId.isNullOrBlank()) {
                            val cust = findCustomerByLineUserId(userId)
                            if (cust != null) {
                                val primaryPhone = cust.identities.firstOrNull { it.identityType == IdentityType.PHONE }?.identityValue
                                matchedList.add(
                                    CustomerSummaryDto(
                                        customerId = cust.customer.id,
                                        displayName = cust.customer.displayName,
                                        tierName = cust.currentTier?.name ?: "STANDARD",
                                        currentPoints = cust.currentPointsBalance,
                                        phone = primaryPhone
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to parse LINE webhook payload JSON: {}", ex.message)
            return LineWebhookResponseDto(
                success = false,
                message = "Invalid JSON payload: ${ex.message}",
                eventCount = 0
            )
        }

        logger.info("Processed LINE webhook: {} events received, {} matched customer profiles found", eventCount, matchedList.size)
        return LineWebhookResponseDto(
            success = true,
            message = "Processed $eventCount LINE events successfully",
            eventCount = eventCount,
            matchedCustomers = matchedList
        )
    }

    fun validateLineSignature(payload: String, signature: String, secret: String): Boolean {
        return try {
            val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)
            val hmacBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            val calcSignature = Base64.getEncoder().encodeToString(hmacBytes)
            calcSignature == signature
        } catch (ex: Exception) {
            false
        }
    }

    @Transactional
    fun notifyOrderStatus(customerId: String, orderId: String, orderStatus: String): NotificationLog? {
        val identities = identityRepository.findByCustomerId(customerId)
        val lineIdentity = identities.firstOrNull { it.identityType == IdentityType.LINE } ?: return null

        val template = when (orderStatus) {
            "CONFIRMED" -> NotificationTemplate.ORDER_CONFIRMED
            "PREPARING" -> NotificationTemplate.KITCHEN_PREPARING
            "READY" -> NotificationTemplate.ORDER_READY
            "COMPLETED" -> NotificationTemplate.ORDER_COMPLETED
            else -> NotificationTemplate.ORDER_CONFIRMED
        }

        return messagingGateway.sendNotification(
            recipientId = lineIdentity.identityValue,
            template = template,
            data = mapOf("orderId" to orderId, "status" to orderStatus)
        )
    }

    fun getLineConfig(): LineOaConfig? {
        return lineConfigRepository.findAll().firstOrNull()
    }

    fun getEffectiveConfig(): LineConfigDto {
        val dbConfig = getLineConfig()
        return LineConfigDto(
            channelId = dbConfig?.channelId ?: channelIdProp.takeIf { it.isNotBlank() },
            channelSecret = dbConfig?.channelSecret ?: channelSecretProp.takeIf { it.isNotBlank() },
            channelAccessToken = dbConfig?.channelAccessToken ?: channelAccessTokenProp.takeIf { it.isNotBlank() },
            enabled = dbConfig?.isActive ?: lineEnabledProp,
            mockMode = mockModeProp
        )
    }

    fun saveLineConfig(config: LineOaConfig): LineOaConfig {
        return lineConfigRepository.save(config)
    }

    fun listNotificationLogs(): List<NotificationLog> = notificationLogRepository.findAll().sortedByDescending { it.createdAt }
}

// ── REST Controller ──

@RestController
@RequestMapping("/api/v1/integration/line")
class LineIntegrationController(
    private val lineService: LineIntegrationService
) {
    @PostMapping("/link")
    fun linkIdentity(
        @RequestBody(required = false) req: LinkLineRequestDto?,
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) lineUserId: String?
    ): ApiResponse<CustomerIdentity> {
        val targetCustId = req?.customerId ?: customerId ?: throw IllegalArgumentException("customerId is required")
        val targetLineId = req?.lineUserId ?: lineUserId ?: throw IllegalArgumentException("lineUserId is required")
        return ApiResponse.success(lineService.linkLineIdentity(targetCustId, targetLineId), "LINE identity linked successfully")
    }

    @PostMapping("/link-identity")
    fun linkIdentityLegacy(
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) lineUserId: String?,
        @RequestBody(required = false) req: LinkLineRequestDto?
    ): ApiResponse<CustomerIdentity> {
        val targetCustId = customerId ?: req?.customerId ?: throw IllegalArgumentException("customerId is required")
        val targetLineId = lineUserId ?: req?.lineUserId ?: throw IllegalArgumentException("lineUserId is required")
        return ApiResponse.success(lineService.linkLineIdentity(targetCustId, targetLineId), "LINE identity linked successfully")
    }

    @PostMapping("/unlink")
    fun unlinkIdentity(
        @RequestBody(required = false) req: UnlinkLineRequestDto?,
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) lineUserId: String?
    ): ApiResponse<Boolean> {
        val targetCustId = req?.customerId ?: customerId ?: throw IllegalArgumentException("customerId is required")
        val targetLineId = req?.lineUserId ?: lineUserId
        val unlinked = lineService.unlinkLineIdentity(targetCustId, targetLineId)
        return ApiResponse.success(unlinked, if (unlinked) "LINE identity unlinked successfully" else "No LINE identity found to unlink")
    }

    @DeleteMapping("/link")
    fun deleteLinkIdentity(
        @RequestParam customerId: String,
        @RequestParam(required = false) lineUserId: String?
    ): ApiResponse<Boolean> {
        val unlinked = lineService.unlinkLineIdentity(customerId, lineUserId)
        return ApiResponse.success(unlinked, if (unlinked) "LINE identity unlinked successfully" else "No LINE identity found to unlink")
    }

    @GetMapping("/customer/{lineUserId}")
    fun getCustomerByLineUserId(@PathVariable lineUserId: String): ApiResponse<CustomerDetailsDto?> {
        val customer = lineService.findCustomerByLineUserId(lineUserId)
        return if (customer != null) {
            ApiResponse.success(customer, "Customer found for LINE User ID")
        } else {
            ApiResponse.success(null, "No customer linked to LINE User ID '$lineUserId'")
        }
    }

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader(value = "X-Line-Signature", required = false) signature: String?
    ): ApiResponse<LineWebhookResponseDto> {
        val result = lineService.processWebhook(payload, signature)
        return if (result.success) {
            ApiResponse.success(result, result.message)
        } else {
            ApiResponse.error("WEBHOOK_FAILED", result.message)
        }
    }

    @GetMapping("/config")
    fun getConfig(): ApiResponse<LineConfigDto> {
        return ApiResponse.success(lineService.getEffectiveConfig())
    }

    @PostMapping("/config")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    fun saveConfig(@RequestBody config: LineOaConfig): ApiResponse<LineOaConfig> {
        return ApiResponse.success(lineService.saveLineConfig(config), "LINE OA Configuration saved successfully")
    }

    @GetMapping("/notification-logs")
    fun listLogs(): ApiResponse<List<NotificationLog>> {
        return ApiResponse.success(lineService.listNotificationLogs())
    }
}
