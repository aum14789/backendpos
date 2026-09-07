package com.sunpos.backend.domain.websocket

import com.sunpos.backend.domain.organization.BranchRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import java.security.Principal

@Component
class ActiveKeyChannelInterceptor(
    private val branchRepository: BranchRepository,
    private val sessionRegistry: BranchSessionRegistry,
    @org.springframework.context.annotation.Lazy
    private val activationCodeRepository: com.sunpos.backend.domain.organization.ActivationCodeRepository? = null
) : ChannelInterceptor {

    private val logger = LoggerFactory.getLogger(ActiveKeyChannelInterceptor::class.java)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            val branchId = accessor.getFirstNativeHeader("branchId")
                ?: accessor.getFirstNativeHeader("branch-id")

            val activeKey = accessor.getFirstNativeHeader("activeKey")
                ?: accessor.getFirstNativeHeader("active-key")

            logger.info("Incoming WebSocket connection attempt from branchId: [{}]", branchId)

            if (branchId.isNullOrBlank() || activeKey.isNullOrBlank() || !isValidActiveKey(branchId.trim(), activeKey.trim())) {
                logger.error("❌ WebSocket connection rejected: Invalid branchId or activeKey for branch [{}]", branchId)
                throw BadCredentialsException("Authentication failed: Invalid branchId or Active Key")
            }

            val validBranchId = branchId.trim()
            val sessionId = accessor.sessionId ?: ""

            accessor.user = Principal { validBranchId }
            sessionRegistry.register(validBranchId, sessionId)
        }

        return message
    }

    private fun isValidActiveKey(branchId: String, activeKey: String): Boolean {
        val cleanKey = activeKey.trim()
        val upperKey = cleanKey.uppercase()

        // 1. Allow standard DEV and default branch keys
        if (upperKey.startsWith("DEV-") || upperKey == "ACT-BRANCH-001" || upperKey == "DEV-BRANCH-001-POS-01") {
            return true
        }

        val branchOpt = branchRepository.findById(branchId)
        if (branchOpt.isEmpty) return false

        val branch = branchOpt.get()
        if (!branch.isActive) return false

        // 2. If branch has no activation code configured yet, allow connection
        if (branch.activationCode.isNullOrBlank()) {
            return true
        }

        // 3. Exact match with branch activation code
        if (branch.activationCode?.trim()?.equals(cleanKey, ignoreCase = true) == true) {
            return true
        }

        // 4. Match with registered activation codes
        val optRecord = activationCodeRepository?.findByCode(cleanKey)
            ?: activationCodeRepository?.findByCode(upperKey)
        if (optRecord != null && optRecord.isPresent) {
            return optRecord.get().branchId == branchId
        }

        return false
    }
}
