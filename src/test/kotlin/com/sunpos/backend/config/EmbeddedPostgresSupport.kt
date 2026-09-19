package com.sunpos.backend.config

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory

/**
 * Owns a single embedded PostgreSQL instance for the whole test JVM.
 *
 * The consolidated baseline (`V1__baseline_schema.sql`) is PostgreSQL-only DDL —
 * it uses `CREATE EXTENSION "uuid-ossp"`, `ON CONFLICT (...) DO UPDATE ... EXCLUDED`
 * and `NULLS LAST`, none of which an in-memory substitute accepts. Running the
 * tests on real PostgreSQL means the suite exercises the same schema and the same
 * SQL as production instead of erroring out before it can assert anything.
 *
 * One instance is shared and started lazily so Spring's context cache does not
 * pay for a fresh cluster per test class.
 */
object EmbeddedPostgresSupport {

    private val logger = LoggerFactory.getLogger(EmbeddedPostgresSupport::class.java)

    private var instance: EmbeddedPostgres? = null

    @Synchronized
    fun get(): EmbeddedPostgres {
        instance?.let { return it }

        val started = EmbeddedPostgres.builder().start()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { started.close() } })

        logger.info("Embedded PostgreSQL ready for tests on port {}", started.port)
        instance = started
        return started
    }

    /** JDBC URL of the `postgres` maintenance database. */
    val jdbcUrl: String
        get() = get().getJdbcUrl(USERNAME, DATABASE)

    const val USERNAME = "postgres"
    const val DATABASE = "postgres"
}
