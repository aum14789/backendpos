package com.sunpos.backend.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${sunpos.jwt.secret:}") private val jwtSecret: String,
    @Value("\${sunpos.jwt.expiration-ms:86400000}") private val jwtExpirationMs: Long
) {

    @PostConstruct
    fun validateConfiguration() {
        if (jwtSecret.isBlank()) {
            throw IllegalStateException(
                "FATAL SECURITY CONFIGURATION ERROR: 'JWT_SECRET' environment variable is missing or blank. " +
                "SunPOS requires a strong secret key (minimum 32 bytes/characters) to be set via environment variable."
            )
        }
        if (jwtSecret.toByteArray(StandardCharsets.UTF_8).size < 32) {
            throw IllegalStateException(
                "FATAL SECURITY CONFIGURATION ERROR: 'JWT_SECRET' is too short (${jwtSecret.length} chars). " +
                "HMAC-SHA256 requires at least 256 bits (32 bytes)."
            )
        }
    }

    private val key: SecretKey
        get() = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(
        username: String,
        authorities: List<String>,
        claimsMap: Map<String, Any> = emptyMap()
    ): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val builder = Jwts.builder()
            .subject(username)
            .claim("authorities", authorities)
            .claim("roles", authorities)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)

        claimsMap.forEach { (k, v) ->
            builder.claim(k, v)
        }

        return builder.compact()
    }

    fun getUsernameFromToken(token: String): String {
        return getClaims(token).subject
    }

    @Suppress("UNCHECKED_CAST")
    fun getAuthoritiesFromToken(token: String): List<String> {
        val claims = getClaims(token)
        val auths = (claims["authorities"] as? List<*>)?.mapNotNull { it?.toString() }
        if (!auths.isNullOrEmpty()) return auths
        return (claims["roles"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    fun getRolesFromToken(token: String): List<String> {
        val claims = getClaims(token)
        return (claims["roles"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
