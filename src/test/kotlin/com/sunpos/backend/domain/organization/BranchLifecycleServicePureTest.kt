package com.sunpos.backend.domain.organization

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*

class BranchLifecycleServicePureTest {

    class FakeBranchRepository : BranchRepository(mock(JdbcTemplate::class.java)) {
        val branches = mutableMapOf<String, Branch>()
        override fun save(entity: Branch): Branch {
            branches[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<Branch> = Optional.ofNullable(branches[id.toString()])
    }

    private lateinit var branchRepository: FakeBranchRepository
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var lifecycleService: BranchLifecycleService

    @BeforeEach
    fun setUp() {
        branchRepository = FakeBranchRepository()
        jdbcTemplate = mock(JdbcTemplate::class.java)
        lifecycleService = BranchLifecycleService(branchRepository, jdbcTemplate)

        // Mock count queries
        `when`(jdbcTemplate.queryForObject(contains("orders WHERE branch_id"), eq(Int::class.java), anyString()))
            .thenReturn(15)
        `when`(jdbcTemplate.queryForObject(contains("payment_transactions WHERE branch_id"), eq(Int::class.java), anyString()))
            .thenReturn(12)
        `when`(jdbcTemplate.queryForObject(contains("cashier_shifts WHERE branch_id"), eq(Int::class.java), anyString()))
            .thenReturn(3)
    }

    @Test
    fun `test purgeBranchTestData in PRE_OPENING successfully purges transactions and resets invoice sequence`() {
        val branch = Branch(
            id = "branch-pinklao",
            name = "Central Pinklao",
            status = "PRE_OPENING",
            isTestBranch = false,
            invoiceSequenceNumber = 42L
        )
        branchRepository.save(branch)

        val summary = lifecycleService.purgeBranchTestData(branch.id, operatorUserId = "admin-1")

        assertEquals(branch.id, summary.branchId)
        assertEquals(15, summary.purgedOrdersCount)
        assertEquals(12, summary.purgedPaymentsCount)
        assertEquals(3, summary.purgedShiftsCount)

        // Verify invoice sequence number was reset to 0
        val updatedBranch = branchRepository.findById(branch.id).get()
        assertEquals(0L, updatedBranch.invoiceSequenceNumber)
        assertEquals("PRE_OPENING", updatedBranch.status)

        // Verify delete queries executed
        verify(jdbcTemplate).update(contains("DELETE FROM payment_transactions"), eq(branch.id))
        verify(jdbcTemplate).update(contains("DELETE FROM orders"), eq(branch.id))
        verify(jdbcTemplate).update(contains("DELETE FROM cashier_shifts"), eq(branch.id))
        verify(jdbcTemplate).update(contains("DELETE FROM table_sessions"), eq(branch.id))
    }

    @Test
    fun `test purgeBranchTestData throws IllegalStateException on active production store`() {
        val branch = Branch(
            id = "branch-live",
            name = "Central World Live",
            status = "ACTIVE",
            isTestBranch = false
        )
        branchRepository.save(branch)

        val exception = assertThrows(IllegalStateException::class.java) {
            lifecycleService.purgeBranchTestData(branch.id)
        }
        assertTrue(exception.message!!.contains("Cannot purge transactions from an active production branch"))

        // Ensure zero deletes executed on active branch
        verify(jdbcTemplate, never()).update(contains("DELETE FROM orders"), anyString())
    }

    @Test
    fun `test activateBranch purges test data and switches status to ACTIVE`() {
        val branch = Branch(
            id = "branch-opening",
            name = "Siam Paragon Opening",
            status = "PRE_OPENING",
            isTestBranch = false,
            invoiceSequenceNumber = 10L
        )
        branchRepository.save(branch)

        val activated = lifecycleService.activateBranch(branch.id, purgeTestData = true, operatorUserId = "admin-owner")

        assertEquals("ACTIVE", activated.status)
        assertEquals(0L, activated.invoiceSequenceNumber)
        assertEquals("admin-owner", activated.updatedBy)

        verify(jdbcTemplate).update(contains("DELETE FROM orders"), eq(branch.id))
    }

    @Test
    fun `test resetTestBranch allows purge on test branch and rejects on non-test branch`() {
        val testBranch = Branch(
            id = "branch-qa-test",
            name = "HQ QA Test Branch",
            status = "ACTIVE",
            isTestBranch = true
        )
        branchRepository.save(testBranch)

        val summary = lifecycleService.resetTestBranch(testBranch.id)
        assertEquals(15, summary.purgedOrdersCount)

        val nonTestBranch = Branch(
            id = "branch-real-store",
            name = "Real Store",
            status = "ACTIVE",
            isTestBranch = false
        )
        branchRepository.save(nonTestBranch)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            lifecycleService.resetTestBranch(nonTestBranch.id)
        }
        assertTrue(ex.message!!.contains("is not marked as a test branch"))
    }
}
