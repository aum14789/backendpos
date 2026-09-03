package com.sunpos.backend.domain.websocket

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class BranchConnectionMeta(
    val branchId: String,
    val sessionId: String,
    val connectedAt: Instant = Instant.now()
)

@Service
class BranchSessionRegistry {

    private val logger = LoggerFactory.getLogger(BranchSessionRegistry::class.java)

    // branchId -> BranchConnectionMeta
    private val branchToMetaMap = ConcurrentHashMap<String, BranchConnectionMeta>()
    // sessionId -> branchId
    private val sessionToBranchMap = ConcurrentHashMap<String, String>()

    fun register(branchId: String, sessionId: String) {
        val oldMeta = branchToMetaMap.put(branchId, BranchConnectionMeta(branchId, sessionId))
        if (oldMeta != null) {
            sessionToBranchMap.remove(oldMeta.sessionId)
        }
        sessionToBranchMap[sessionId] = branchId
        logger.info("🟢 Branch [{}] CONNECTED with Session [{}] (Online branches: {})", branchId, sessionId, branchToMetaMap.size)
    }

    fun getSessionId(branchId: String): String? {
        return branchToMetaMap[branchId]?.sessionId
    }

    fun getConnectionMeta(branchId: String): BranchConnectionMeta? {
        return branchToMetaMap[branchId]
    }

    fun isBranchOnline(branchId: String): Boolean {
        return branchToMetaMap.containsKey(branchId)
    }

    fun getOnlineBranches(): Set<String> {
        return branchToMetaMap.keys.toSet()
    }

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId
        val branchId = sessionToBranchMap.remove(sessionId)
        if (branchId != null) {
            branchToMetaMap.remove(branchId)
            logger.warn("🔴 Branch [{}] DISCONNECTED (Session: {}) (Remaining Online: {})", branchId, sessionId, branchToMetaMap.size)
        }
    }
}
