package com.sunpos.backend.domain.organization

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Spec 0036 (ผู้ใช้เพิ่ม): ลบแบรนด์ได้เมื่อไม่มีสาขาในแบรนด์เลยเท่านั้น —
 * ไม่ว่าสาขาจะสถานะใดหรือ active หรือไม่ ขอแค่มีสาขาอยู่ก็ลบไม่ได้
 * และการลบต้องทำให้แบรนด์หายจากรายการจริง (soft-delete + ซ่อน)
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = ["SUPER_ADMIN"])
@Transactional
class BrandDeletionTest {

    @Autowired private lateinit var organizationController: OrganizationController
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val brandId = "brand-del-01"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBrand(brandId, "comp-001", "แบรนด์ทดสอบ", "DEL-01")
    }

    @Test
    fun `brand with a branch cannot be deleted regardless of branch status`() {
        testFixtureFactory.ensureBranch(id = "branch-under-brand-01", brandId = brandId, name = "สาขาในแบรนด์ 1")
        jdbc.update("UPDATE branches SET status = 'ACTIVE' WHERE id = 'branch-under-brand-01'")

        val ex = assertThrows(IllegalStateException::class.java) {
            organizationController.deleteBrand(brandId)
        }
        assertTrue(ex.message!!.contains("สาขา"))
    }

    @Test
    fun `brand with an inactive branch still cannot be deleted`() {
        testFixtureFactory.ensureBranch(id = "branch-under-brand-02", brandId = brandId, name = "สาขาในแบรนด์ 2")
        jdbc.update("UPDATE branches SET is_active = false WHERE id = 'branch-under-brand-02'")

        assertThrows(IllegalStateException::class.java) {
            organizationController.deleteBrand(brandId)
        }
    }

    @Test
    fun `brand with a soft-deleted branch still cannot be deleted`() {
        testFixtureFactory.ensureBranch(id = "branch-under-brand-03", brandId = brandId, name = "สาขาในแบรนด์ 3")
        jdbc.update("UPDATE branches SET is_active = false WHERE id = 'branch-under-brand-03'")
        jdbc.update("UPDATE branches SET status = 'CLOSED' WHERE id = 'branch-under-brand-03'")

        assertThrows(IllegalStateException::class.java) {
            organizationController.deleteBrand(brandId)
        }
    }

    @Test
    fun `empty brand is soft-deleted and disappears from the brand list`() {
        val result = organizationController.deleteBrand(brandId)
        assertTrue(result.data == true)

        val row = jdbc.queryForMap("SELECT is_active FROM brands WHERE id = ?", brandId)
        assertEquals(false, row["is_active"])

        val visible = jdbc.queryForObject(
            "SELECT COUNT(*) FROM brands WHERE id = ? AND is_active = true", Long::class.java, brandId
        )
        assertEquals(0L, visible)

        // API getBrands hides inactive brands by default
        val brandList = organizationController.getBrands(null).data!!
        assertFalse(brandList.any { it.id == brandId })

        // When explicitly requested, inactive brands can still be inspected
        val allBrands = organizationController.getBrands(null, includeInactive = true).data!!
        assertTrue(allBrands.any { it.id == brandId })
    }

    @Test
    fun `delete error message tells how many branches remain`() {
        testFixtureFactory.ensureBranch(id = "branch-under-brand-04", brandId = brandId, name = "สาขาในแบรนด์ 4")
        testFixtureFactory.ensureBranch(id = "branch-under-brand-05", brandId = brandId, name = "สาขาในแบรนด์ 5")

        val ex = assertThrows(IllegalStateException::class.java) {
            organizationController.deleteBrand(brandId)
        }
        assertTrue(ex.message!!.contains("2"))
    }
}
