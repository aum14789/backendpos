package com.sunpos.backend.domain.table

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Spec: adding a table without a zone must be impossible.
 * The zone must be a real, existing zone of the SAME branch — no free-text,
 * no silent null, no cross-branch zone.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TableZoneRequiredTest {

    @Autowired private lateinit var tableService: TableService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory

    private val branchId = "branch-table-zone-01"
    private val otherBranchId = "branch-table-zone-02"
    private val zoneId = "zone-table-zone-01"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
        testFixtureFactory.ensureBranch(otherBranchId)
        testFixtureFactory.ensureZone(id = zoneId, branchId = branchId, name = "Hall A")
    }

    @Test
    fun `create table without zone is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            tableService.createTable(
                TableCreateDto(branchId = branchId, zoneId = null, nameNumber = "T-99", capacity = 4)
            )
        }
        assertTrue(ex.message!!.contains("zone", ignoreCase = true))
    }

    @Test
    fun `create table with blank zone is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            tableService.createTable(
                TableCreateDto(branchId = branchId, zoneId = "", nameNumber = "T-98", capacity = 4)
            )
        }
    }

    @Test
    fun `create table with zone from another branch is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            tableService.createTable(
                TableCreateDto(branchId = otherBranchId, zoneId = zoneId, nameNumber = "T-97", capacity = 4)
            )
        }
    }

    @Test
    fun `create table with valid same-branch zone succeeds`() {
        val table = tableService.createTable(
            TableCreateDto(branchId = branchId, zoneId = zoneId, nameNumber = "T-01", capacity = 4)
        )
        assertEquals(zoneId, table.zoneId)
    }

    @Test
    fun `update table without zone is rejected`() {
        val table = tableService.createTable(
            TableCreateDto(branchId = branchId, zoneId = zoneId, nameNumber = "T-02", capacity = 4)
        )
        assertThrows(IllegalArgumentException::class.java) {
            tableService.updateTable(table.id, TableUpdateDto(zoneId = null, nameNumber = "T-02", capacity = 4))
        }
    }

    @Test
    fun `update table with zone from another branch is rejected`() {
        val table = tableService.createTable(
            TableCreateDto(branchId = branchId, zoneId = zoneId, nameNumber = "T-03", capacity = 4)
        )
        assertThrows(IllegalArgumentException::class.java) {
            tableService.updateTable(table.id, TableUpdateDto(zoneId = "zone-of-other-branch", nameNumber = "T-03", capacity = 4))
        }
    }
}
