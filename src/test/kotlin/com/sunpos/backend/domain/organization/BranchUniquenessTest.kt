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
 * Spec 0036 (ผู้ใช้เพิ่ม): ชื่อและรหัสสาขาต้องไม่ซ้ำกับที่มีอยู่แล้ว
 * - มี endpoint เช็คซ้ำให้กดปุ่ม check ก่อนบันทึก (ไม่ real-time)
 * - ฐานข้อมูลกันซ้ำเป็นชั้นสุดท้าย (unique constraint)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BranchUniquenessTest {

    @Autowired private lateinit var branchLifecycleService: BranchLifecycleService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val branchId = "branch-uniq-01"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId, name = "สาขาเดิม", code = "ORIG-01")
    }

    @Test
    fun `duplicate code is reported`() {
        val result = branchLifecycleService.checkNameAndCodeAvailable(name = "ชื่ออื่น", code = "ORIG-01", excludeBranchId = null)
        assertFalse(result.codeAvailable)
        assertTrue(result.nameAvailable)
    }

    @Test
    fun `duplicate name is reported`() {
        val result = branchLifecycleService.checkNameAndCodeAvailable(name = "สาขาเดิม", code = "NEW-99", excludeBranchId = null)
        assertFalse(result.nameAvailable)
        assertTrue(result.codeAvailable)
    }

    @Test
    fun `fresh name and code are both available`() {
        val result = branchLifecycleService.checkNameAndCodeAvailable(name = "สาขาใหม่", code = "NEW-98", excludeBranchId = null)
        assertTrue(result.nameAvailable)
        assertTrue(result.codeAvailable)
    }

    @Test
    fun `same branch keeping its own name and code is available`() {
        val result = branchLifecycleService.checkNameAndCodeAvailable(name = "สาขาเดิม", code = "ORIG-01", excludeBranchId = branchId)
        assertTrue(result.nameAvailable)
        assertTrue(result.codeAvailable)
    }

    @Test
    fun `database rejects duplicate code as last resort`() {
        val ex = assertThrows(Exception::class.java) {
            jdbc.update(
                """INSERT INTO branches (id, company_id, brand_id, name, code, status)
                   VALUES ('branch-uniq-02', 'comp-001', 'brand-001', 'สาขาซ้ำ', 'ORIG-01', 'PRE_OPENING')"""
            )
        }
        assertTrue(ex.message!!.contains("branches_code_key") || ex.cause?.message?.contains("branches_code_key") == true)
    }
}
