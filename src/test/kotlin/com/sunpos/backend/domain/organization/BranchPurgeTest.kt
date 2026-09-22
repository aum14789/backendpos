package com.sunpos.backend.domain.organization

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Bug: purging a branch's test data always failed with 500
 * "current transaction is aborted" because the table-reset statement referenced
 * a non-existent column (current_order_id) inside try/catch — on PostgreSQL the
 * failed statement aborts the whole transaction, so the swallowed fallback died
 * with SQLState 25P02.
 * The purge must succeed on a branch that has no transactions at all.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BranchPurgeTest {

    @Autowired private lateinit var branchLifecycleService: BranchLifecycleService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val branchId = "branch-purge-01"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
    }

    @Test
    fun `purge succeeds on a branch with no transactions`() {
        val summary = branchLifecycleService.purgeBranchTestData(branchId)
        assertEquals(0, summary.purgedOrdersCount)
        assertEquals(0, summary.purgedPaymentsCount)
        assertEquals(0, summary.purgedShiftsCount)
    }

    @Test
    fun `purge succeeds on a branch with a table`() {
        testFixtureFactory.ensureTable(
            id = "tbl-purge-01",
            branchId = branchId,
            nameNumber = "P-01"
        )
        val summary = branchLifecycleService.purgeBranchTestData(branchId)
        assertEquals(0, summary.purgedOrdersCount)
    }

    @Test
    fun `purge refuses an active production branch`() {
        jdbc.update("UPDATE branches SET status = 'ACTIVE', is_test_branch = false WHERE id = ?", branchId)

        assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.purgeBranchTestData(branchId)
        }
    }

    @Test
    fun `purge clears sales data of a test branch`() {
        testFixtureFactory.ensureOrder(id = "ord-purge-01", branchId = branchId, orderNumber = "ORD-P1")
        jdbc.update(
            """INSERT INTO payment_transactions (id, branch_id, order_id, amount, payment_method, status)
               VALUES ('pay-purge-01', ?, 'ord-purge-01', 10000, 'CASH', 'SUCCESS')""",
            branchId
        )
        val summary = branchLifecycleService.purgeBranchTestData(branchId)
        assertEquals(1, summary.purgedOrdersCount)
        assertEquals(1, summary.purgedPaymentsCount)
        val remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE branch_id = ?", Int::class.java, branchId
        )
        assertEquals(0, remaining)
    }
}
