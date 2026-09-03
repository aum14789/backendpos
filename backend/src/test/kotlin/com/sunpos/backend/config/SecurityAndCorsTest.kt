package com.sunpos.backend.config

import com.sunpos.backend.domain.organization.Branch
import com.sunpos.backend.domain.organization.BranchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndCorsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Test
    fun `test public endpoints allow unauthenticated access`() {
        // GET /api/public/menu/{branchId} without any token
        mockMvc.perform(get("/api/public/menu/BR-SEC-TEST"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.branchId").value("BR-SEC-TEST"))

        // POST /api/public/orders without any token
        val orderBody = """
            {
                "branchId": "BR-SEC-TEST",
                "tableNumber": "1",
                "customerNote": "ไม่เผ็ด",
                "items": [
                    {
                        "productId": "P01",
                        "productName": "ข้าวผัด",
                        "quantity": 1,
                        "unitPrice": 80.00
                    }
                ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/public/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "sec-test-key-1")
                .content(orderBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("pending"))
    }

    @Test
    fun `test CORS preflight allows frontend origins and required headers`() {
        mockMvc.perform(
            options("/api/public/orders")
                .header("Origin", "http://localhost:5174")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,Idempotency-Key")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists("Access-Control-Allow-Origin"))
            .andExpect(header().exists("Access-Control-Allow-Methods"))
    }

    @Test
    fun `test internal endpoint requires active key or internal secret`() {
        val testBranchId = "BR-INTERNAL-01"
        val testKey = "SUN-SEC-KEY-999"

        branchRepository.save(
            Branch(
                id = testBranchId,
                companyId = "CMP-01",
                brandId = "BRD-01",
                name = "Internal Auth Test Branch",
                code = "IAT01",
                activationCode = testKey,
                isActive = true
            )
        )

        // 1. Without credentials -> 401 Unauthorized
        mockMvc.perform(get("/api/internal/branches/$testBranchId/status"))
            .andExpect(status().isUnauthorized)

        // 2. With invalid active key -> 401 Unauthorized
        mockMvc.perform(
            get("/api/internal/branches/$testBranchId/status")
                .header("branchId", testBranchId)
                .header("activeKey", "WRONG-KEY")
        )
            .andExpect(status().isUnauthorized)

        // 3. With valid active key headers -> 200 OK
        mockMvc.perform(
            get("/api/internal/branches/$testBranchId/status")
                .header("branchId", testBranchId)
                .header("activeKey", testKey)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.branchId").value(testBranchId))

        // 4. With internal secret token header -> 200 OK
        mockMvc.perform(
            get("/api/internal/branches/$testBranchId/status")
                .header("X-Internal-Secret", "sunpos-internal-secret-token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.branchId").value(testBranchId))
    }

    @Test
    fun `test employee POS endpoint requires JWT authentication`() {
        // Without JWT -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/orders"))
            .andExpect(status().isUnauthorized)

        // With valid Mock Cashier JWT -> 200 OK
        mockMvc.perform(
            get("/api/v1/orders")
                .header("Authorization", "Bearer jwt_mock_cashier_token")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `test public rate limit filter rejects excessive requests`() {
        val orderBody = """
            {
                "branchId": "BR-RATE-TEST",
                "tableNumber": "5",
                "items": [{"productId":"P1","productName":"ข้าว","quantity":1,"unitPrice":50.0}]
            }
        """.trimIndent()

        var hit429 = false
        // Trigger rapid requests from the same client IP
        for (i in 1..35) {
            val result = mockMvc.perform(
                post("/api/public/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "203.0.113.195")
                    .header("Idempotency-Key", "rate-limit-test-$i")
                    .content(orderBody)
            ).andReturn()

            if (result.response.status == 429) {
                hit429 = true
                assertEquals("60", result.response.getHeader("Retry-After"))
                break
            }
        }

        assertEquals(true, hit429, "Expected PublicRateLimitFilter to return 429 when threshold exceeded")
    }
}
