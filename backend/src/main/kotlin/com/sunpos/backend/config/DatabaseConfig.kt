package com.sunpos.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.io.File
import java.net.URI
import javax.sql.DataSource

@Configuration
class DatabaseConfig {

    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    @Value("\${spring.datasource.url:}")
    private var rawDatasourceUrl: String = ""

    @Value("\${spring.datasource.username:}")
    private var configuredUsername: String = ""

    @Value("\${spring.datasource.password:}")
    private var configuredPassword: String = ""

    private fun resolveDatabaseUrl(): String {
        // 1. Check system environment
        val envUrl = System.getenv("DATABASE_URL")
        if (!envUrl.isNullOrBlank()) return envUrl

        // 2. Check spring property
        if (rawDatasourceUrl.isNotBlank()) return rawDatasourceUrl

        // 3. Try reading .env file from working directory
        try {
            val envFile = File(".env")
            if (envFile.exists()) {
                envFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("DATABASE_URL=")) {
                        val parsed = trimmed.substringAfter("DATABASE_URL=").trim()
                        if (parsed.isNotBlank()) return parsed
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Could not read .env: {}", e.message)
        }

        // 4. Default fallback when neither DATABASE_URL env nor .env is present
        return "jdbc:postgresql://localhost:5432/neondb"
    }

    @Bean
    @Primary
    fun dataSource(): DataSource {
        val rawUrl = resolveDatabaseUrl()

        // If H2 in-memory (e.g. during test profile)
        if (rawUrl.startsWith("jdbc:h2:")) {
            val config = HikariConfig().apply {
                jdbcUrl = rawUrl
                driverClassName = "org.h2.Driver"
                username = configuredUsername.ifBlank { "sa" }
                password = configuredPassword
            }
            return HikariDataSource(config)
        }

        // Parse PostgreSQL URI if in URI format (e.g. postgresql://user:pass@host:port/db?params)
        var username = configuredUsername
        var password = configuredPassword
        var jdbcUrl = rawUrl

        val cleanUriString = when {
            rawUrl.startsWith("jdbc:postgresql://") -> rawUrl.removePrefix("jdbc:")
            rawUrl.startsWith("postgresql://") -> rawUrl
            else -> null
        }

        if (cleanUriString != null && cleanUriString.contains("@")) {
            try {
                val httpUri = URI(cleanUriString.replaceFirst("postgresql://", "http://"))
                val userInfo = httpUri.userInfo
                if (!userInfo.isNullOrBlank()) {
                    val parts = userInfo.split(":")
                    if (username.isBlank() && parts.isNotEmpty()) username = parts[0]
                    if (password.isBlank() && parts.size > 1) password = parts[1]
                }
                val host = httpUri.host
                val port = if (httpUri.port != -1) httpUri.port else 5432
                val path = httpUri.path
                val query = httpUri.query

                jdbcUrl = "jdbc:postgresql://$host:$port$path" + if (!query.isNullOrBlank()) "?$query" else ""
            } catch (e: Exception) {
                logger.warn("Failed parsing postgres URI, using raw with jdbc: prefix: {}", e.message)
                if (!jdbcUrl.startsWith("jdbc:")) jdbcUrl = "jdbc:$jdbcUrl"
            }
        } else {
            if (!jdbcUrl.startsWith("jdbc:")) jdbcUrl = "jdbc:$jdbcUrl"
        }

        logger.info("Initializing HikariDataSource for Neon PostgreSQL: {}", jdbcUrl.replace(Regex("password=[^&]*"), "password=***"))

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.driverClassName = "org.postgresql.Driver"
            if (username.isNotBlank()) this.username = username
            if (password.isNotBlank()) this.password = password
            this.maximumPoolSize = 5
            this.minimumIdle = 1
            this.connectionTimeout = 20000
            this.idleTimeout = 300000
            this.maxLifetime = 900000
            this.connectionTestQuery = "SELECT 1"
        }

        return HikariDataSource(config)
    }

    @Bean
    @Primary
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate {
        return JdbcTemplate(dataSource)
    }
}
