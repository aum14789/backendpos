package com.sunpos.backend.config

import com.sunpos.backend.domain.organization.BranchRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider,
    @Lazy private val branchRepository: BranchRepository
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        // Support frontend origins: Localhost (5173 POS, 5174 QR Order), Local IP, and Production domains
        configuration.allowedOriginPatterns = listOf(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "https://*.sunpos.app",
            "https://*.vercel.app",
            "https://*.netlify.app",
            "*"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")
        configuration.allowedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Idempotency-Key",
            "branchId",
            "activeKey",
            "X-Branch-Id",
            "X-Active-Key",
            "X-Internal-Secret"
        )
        configuration.exposedHeaders = listOf("Idempotency-Key", "Content-Disposition")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .headers { headers ->
                headers
                    .frameOptions { it.deny() }
                    .contentTypeOptions { }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"success":false,"error":{"code":"UNAUTHORIZED","message":"Full authentication is required to access this resource"}}""")
                }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // 1. Permit all preflight OPTIONS requests
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // 2. Permit Public QR Ordering Endpoints (Customer Mobile Web - Zero Authentication)
                    .requestMatchers(
                        "/api/public/**",
                        "/api/v1/qr/**"
                    ).permitAll()

                    // 3. Permit WebSocket STOMP endpoint for branch outbound handshake
                    .requestMatchers("/ws/**").permitAll()

                    // 4. Permit public auth, device activation, sync, and health endpoints
                    .requestMatchers(
                        "/api/v1/auth/pin-login",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/organization/branches/resolve-current",
                        "/api/v1/organization/devices/activate",
                        "/api/v1/organization/activation-codes/**",
                        "/api/v1/sync/**",
                        "/sync/**",
                        "/api/device/**",
                        "/api/admin/activation-codes/**",
                        "/error",
                        "/actuator/health"
                    ).permitAll()

                    // 5. Protected Internal Endpoints (Requires Active Key or Internal Token authentication)
                    .requestMatchers("/api/internal/**").hasAnyRole("BRANCH_SERVICE", "INTERNAL", "SUPER_ADMIN", "ADMIN")

                    // 6. Employee POS / Backoffice Endpoints (Requires JWT authentication)
                    .requestMatchers("/api/v1/**").authenticated()
                    .anyRequest().authenticated()
            }
            // Rate Limiting Guard for Public Orders
            .addFilterBefore(PublicRateLimitFilter(maxRequestsPerMinute = 30), UsernamePasswordAuthenticationFilter::class.java)
            // Branch Active Key & Internal Auth Guard
            .addFilterBefore(InternalAuthFilter(branchRepository), UsernamePasswordAuthenticationFilter::class.java)
            // Employee JWT Authentication Filter
            .addFilterBefore(JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}

// Filter validating Branch Active Key or Internal Secret on /api/internal
class InternalAuthFilter(
    private val branchRepository: BranchRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        if (path.startsWith("/api/internal/")) {
            // Check headers: branchId / activeKey or internal secret
            val branchId = request.getHeader("branchId")
                ?: request.getHeader("X-Branch-Id")
                ?: request.getHeader("branch-id")

            val activeKey = request.getHeader("activeKey")
                ?: request.getHeader("X-Active-Key")
                ?: request.getHeader("active-key")

            val internalSecret = request.getHeader("X-Internal-Secret")

            val isSecretValid = !internalSecret.isNullOrBlank() && internalSecret == "sunpos-internal-secret-token"
            val isKeyValid = !branchId.isNullOrBlank() && !activeKey.isNullOrBlank() && isValidActiveKey(branchId.trim(), activeKey.trim())

            if (isSecretValid || isKeyValid) {
                val principal = branchId ?: "INTERNAL_SERVICE"
                val auth = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_BRANCH_SERVICE"), SimpleGrantedAuthority("ROLE_INTERNAL"))
                )
                SecurityContextHolder.getContext().authentication = auth
            } else if (SecurityContextHolder.getContext().authentication == null) {
                // If neither branch active key nor existing authenticated admin session is present, reject
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "application/json;charset=UTF-8"
                response.writer.write("""{"success":false,"error":{"code":"UNAUTHORIZED","message":"Invalid or missing Branch Active Key / Internal credentials"}}""")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun isValidActiveKey(branchId: String, activeKey: String): Boolean {
        return try {
            val branchOpt = branchRepository.findById(branchId)
            if (branchOpt.isEmpty) return false
            val branch = branchOpt.get()
            if (!branch.isActive) return false
            branch.activationCode?.equals(activeKey, ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }
}

// In-memory sliding window rate limiter for public order creation
class PublicRateLimitFilter(
    private val maxRequestsPerMinute: Int = 30
) : OncePerRequestFilter() {

    private data class RequestTracker(var count: Int, var windowStart: Long)
    private val clientMap = ConcurrentHashMap<String, RequestTracker>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val method = request.method

        if (method.equals("POST", ignoreCase = true) && path.startsWith("/api/public/orders")) {
            val clientIp = getClientIp(request)
            val now = System.currentTimeMillis()

            val tracker = clientMap.compute(clientIp) { _, current ->
                if (current == null || now - current.windowStart > 60_000) {
                    RequestTracker(count = 1, windowStart = now)
                } else {
                    current.count++
                    current
                }
            }

            if (tracker != null && tracker.count > maxRequestsPerMinute) {
                response.status = 429 // Too Many Requests
                response.setHeader("Retry-After", "60")
                response.contentType = "application/json;charset=UTF-8"
                response.writer.write("""{"success":false,"error":{"code":"RATE_LIMIT_EXCEEDED","message":"Too many orders submitted from this IP. Please wait a moment before trying again."}}""")
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            xForwardedFor.split(",")[0].trim()
        } else {
            request.remoteAddr ?: "unknown"
        }
    }
}

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            if (jwtTokenProvider.validateToken(token)) {
                val username = jwtTokenProvider.getUsernameFromToken(token)
                val authoritiesList = jwtTokenProvider.getAuthoritiesFromToken(token)
                val authorities = authoritiesList.map { SimpleGrantedAuthority(it) }

                val auth = UsernamePasswordAuthenticationToken(username, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            } else if (token.startsWith("jwt_mock_") || token.startsWith("jwt_token_")) {
                // Development / Mock Session Authentication Handler for Seamless Testing
                val isSuper = token.contains("admin") || token.contains("hq") || token.contains("super")
                val isOffice = token.contains("office")
                val isManager = token.contains("manager")
                val isWarehouse = token.contains("warehouse") || token.contains("wh")
                val authorities = if (isSuper) {
                    listOf(
                        "ROLE_SUPER_ADMIN", "ORGANIZATION_MANAGE", "SYSTEM_CONFIG",
                        "CATALOG_DISTRIBUTE", "INVENTORY_DISTRIBUTE", "BRAND_MANAGE",
                        "BRANCH_MANAGE", "DEVICE_MANAGE", "USER_MANAGE", "ROLE_MANAGE",
                        "MENU_MANAGE", "INVENTORY_VIEW", "INVENTORY_ITEM_MANAGE",
                        "STOCK_ADJUST", "STOCK_TRANSFER", "STOCK_COUNT", "RECIPE_MANAGE",
                        "PRODUCTION_MANAGE", "PURCHASE_ORDER", "GOODS_RECEIVE", "SUPPLIER_MANAGE",
                        "CRM_MANAGE", "REPORT_SALES_VIEW", "REPORT_FINANCIAL_VIEW",
                        "REPORT_EXECUTIVE_VIEW", "REPORT_INVENTORY_VIEW"
                    ).map { SimpleGrantedAuthority(it) }
                } else if (isOffice) {
                    listOf(
                        "ROLE_OFFICE_STAFF", "MENU_MANAGE", "BUFFET_MANAGE", "RECIPE_MANAGE",
                        "PRODUCTION_MANAGE", "SUPPLIER_MANAGE", "PURCHASE_ORDER", "GOODS_RECEIVE",
                        "CRM_MANAGE", "INVENTORY_VIEW", "INVENTORY_ITEM_MANAGE", "REPORT_SALES_VIEW",
                        "REPORT_FINANCIAL_VIEW", "REPORT_INVENTORY_VIEW"
                    ).map { SimpleGrantedAuthority(it) }
                } else if (isManager) {
                    listOf(
                        "ROLE_BRANCH_MANAGER", "ROLE_STORE_MANAGER", "ORGANIZATION_MANAGE",
                        "ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL", "ORDER_VOID",
                        "DISCOUNT_APPLY", "DISCOUNT_OVERRIDE", "PAYMENT_MANAGE", "PAYMENT_REFUND",
                        "SHIFT_MANAGE", "MENU_MANAGE", "BUFFET_MANAGE", "INVENTORY_VIEW",
                        "INVENTORY_ITEM_MANAGE", "STOCK_ADJUST", "STOCK_TRANSFER", "STOCK_COUNT",
                        "RECIPE_MANAGE", "PURCHASE_ORDER", "GOODS_RECEIVE", "CRM_MANAGE", "REPORT_SALES_VIEW"
                    ).map { SimpleGrantedAuthority(it) }
                } else if (isWarehouse) {
                    listOf(
                        "ROLE_WAREHOUSE_STAFF", "INVENTORY_VIEW", "INVENTORY_ITEM_MANAGE",
                        "STOCK_ADJUST", "STOCK_TRANSFER", "STOCK_COUNT", "PURCHASE_ORDER",
                        "GOODS_RECEIVE", "SUPPLIER_MANAGE", "REPORT_INVENTORY_VIEW"
                    ).map { SimpleGrantedAuthority(it) }
                } else {
                    listOf(
                        "ROLE_CASHIER", "ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL",
                        "DISCOUNT_APPLY", "PAYMENT_MANAGE", "SHIFT_MANAGE", "MENU_MANAGE", "CRM_MANAGE"
                    ).map { SimpleGrantedAuthority(it) }
                }
                val auth = UsernamePasswordAuthenticationToken("dev_admin", null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
