package com.sunpos.backend.domain.crm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * `created_at` is microsecond-precision, so two ledger entries written in the same
 * instant tie under `ORDER BY created_at DESC` and the "newest entry" is arbitrary.
 * The balance must not depend on that ordering.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PointBalanceLedgerTest {

    @Autowired
    private lateinit var crmService: CrmService

    @Autowired
    private lateinit var pointLedgerRepository: PointLedgerRepository

    @Test
    fun `balance is correct when a redeem shares its created_at instant with an earn`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Tie Tester", phone = "0891234000")
        )

        val earn = crmService.earnPointsForOrder(cust.customer.id, "ORD-TIE-A", BigDecimal("2500.00"))
        assertEquals(BigDecimal("100.0000"), earn.points)
        assertEquals(BigDecimal("100.0000"), crmService.calculatePointsBalance(cust.customer.id))

        // Deliberately reuse the earn's instant: ordering alone cannot resolve which entry came last.
        pointLedgerRepository.save(
            PointLedger(
                customerId = cust.customer.id,
                transactionType = PointTransactionType.REDEEM,
                points = BigDecimal("-30.0000"),
                balanceAfter = BigDecimal("70.0000"),
                referenceType = "ORDER",
                referenceId = "ORD-TIE-B",
                createdAt = earn.createdAt
            )
        )

        assertEquals(BigDecimal("70.0000"), crmService.calculatePointsBalance(cust.customer.id))
        assertEquals(BigDecimal("70.0000"), crmService.getPointBalanceDetails(cust.customer.id).balance)
    }
}
