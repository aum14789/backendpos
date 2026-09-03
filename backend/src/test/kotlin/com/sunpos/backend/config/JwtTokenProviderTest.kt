package com.sunpos.backend.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JwtTokenProviderTest {

    private val jwtSecret = "8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b"
    private val expirationMs = 3600000L // 1 hr

    private val tokenProvider = JwtTokenProvider(jwtSecret, expirationMs)

    @Test
    fun `test generate and parse JWT token`() {
        val username = "cashier01"
        val roles = listOf("ROLE_CASHIER", "ORDER_CREATE")

        val token = tokenProvider.generateToken(username, roles)
        assertNotNull(token)
        assertTrue(tokenProvider.validateToken(token))

        val parsedUsername = tokenProvider.getUsernameFromToken(token)
        val parsedRoles = tokenProvider.getRolesFromToken(token)

        assertEquals(username, parsedUsername)
        assertTrue(parsedRoles.contains("ROLE_CASHIER"))
        assertTrue(parsedRoles.contains("ORDER_CREATE"))
    }
}
