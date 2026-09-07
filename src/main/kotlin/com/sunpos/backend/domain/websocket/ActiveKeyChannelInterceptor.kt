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
    private val sessionRegistry: BranchSessionRegistry
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
        val branchOpt = branchRepository.findById(branchId)
        if (branchOpt.isEmpty) return false

        val branch = branchOpt.get()
        if (!branch.isActive) return false

        return branch.activationCode?.equals(activeKey, ignoreCase = true) == true
    }
}
