package com.sunpos.backend.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Swaps the `test` profile's datasource for the embedded PostgreSQL instance.
 *
 * Registered through `META-INF/spring.factories` so every `@SpringBootTest` picks
 * it up without touching the 24 test classes that share the profile.
 */
class EmbeddedPostgresEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (!environment.activeProfiles.contains(TEST_PROFILE)) return

        val jdbcUrl = EmbeddedPostgresSupport.jdbcUrl

        environment.propertySources.addFirst(
            MapPropertySource(
                "embeddedPostgres",
                mapOf(
                    "spring.datasource.url" to jdbcUrl,
                    "spring.datasource.username" to EmbeddedPostgresSupport.USERNAME,
                    "spring.datasource.password" to "",
                    "spring.datasource.driver-class-name" to "org.postgresql.Driver",
                )
            )
        )
    }

    private companion object {
        const val TEST_PROFILE = "test"
    }
}
