package com.sunpos.backend.config

import com.sunpos.backend.common.JdbcRepository
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener

/**
 * Clears every repository's in-memory fallback cache before each test method.
 *
 * [JdbcRepository] keeps a `localCache` that it serves reads from when a query returns
 * nothing, which is what lets a unit test run against a stubbed `JdbcTemplate`. In a
 * Spring test however the cache outlives the test transaction: a test that saves a
 * configuration row, a menu item or a ledger entry rolls the row back, yet the cached
 * copy survives. The next test then sees data that does not exist in the database --
 * "menu item already exists" errors, and an inventory config stuck in REALTIME which
 * silently skips stock deduction.
 *
 * Registered through `META-INF/spring.factories`, so it applies to every Spring test
 * without each class having to opt in.
 */
class LocalCacheResetListener : AbstractTestExecutionListener() {

    override fun beforeTestMethod(testContext: TestContext) {
        val repositories = testContext.applicationContext.getBeansOfType(JdbcRepository::class.java)
        repositories.values.forEach { it.clearLocalCache() }
    }
}
