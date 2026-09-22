package com.sunpos.backend.domain.crm

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * Bug report from the deployed backoffice: the CRM report page lists customers via
 * GET /customers, but the unified CustomerController only exposed POST on that path
 * — the list lived on the legacy GET /crm/customers — so the page got
 * "Request method 'GET' is not supported" (500).
 * This test pins the read side on the unified controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerListRouteTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser(username = "crm-list-user")
    fun `GET customers lists customers for the company`() {
        mockMvc.perform(get("/api/v1/customers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
    }

    @Test
    @WithMockUser(username = "crm-list-user")
    fun `legacy crm customers list still works`() {
        mockMvc.perform(get("/api/v1/crm/customers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
    }
}
