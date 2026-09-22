package com.sunpos.backend.identity

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Spec 0035 ticket 04: BRAND_MANAGE and BRANCH_MANAGE are referenced by the
 * backoffice sidebar and controllers but were never seeded into the
 * permissions table — the role management page could not assign them.
 * They must exist in the baseline seed and be granted to ROLE_SUPER_ADMIN.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrandBranchPermissionSeedTest {

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `baseline seeds BRAND_MANAGE and BRANCH_MANAGE permissions`() {
        val codes = jdbcTemplate.queryForList(
            "SELECT code FROM permissions WHERE code IN ('BRAND_MANAGE', 'BRANCH_MANAGE')",
            String::class.java
        )
        assertTrue(codes.contains("BRAND_MANAGE"), "BRAND_MANAGE must be seeded")
        assertTrue(codes.contains("BRANCH_MANAGE"), "BRANCH_MANAGE must be seeded")
    }

    @Test
    fun `both permissions are granted to ROLE_SUPER_ADMIN`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM role_permissions rp
            JOIN roles r ON r.id = rp.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE r.name = 'ROLE_SUPER_ADMIN' AND p.code IN ('BRAND_MANAGE', 'BRANCH_MANAGE')
            """.trimIndent(),
            Integer::class.java
        ) ?: 0
        assertTrue(count >= 2, "Super Admin must hold both permissions, got $count")
    }
}
