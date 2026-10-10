package net.nemerosa.ontrack.service.support

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Old Flyway versions recorded the Java migrations as `SPRING_JDBC`, a type the Flyway of Yontrack 6
 * no longer knows (#2050): [StartupStrategy] relabels them as `JDBC` before migrating.
 *
 * Each test runs against a database of its own, created on the IT Postgres server, then dropped.
 */
class StartupStrategyIT {

    private val url = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/ontrack")
    private val username = System.getProperty("spring.datasource.username", "ontrack")
    private val password = System.getProperty("spring.datasource.password", "ontrack")

    @Test
    fun `A SPRING_JDBC row of the schema history is relabelled as JDBC before migrating`() {
        withDatabase { flyway, jdbc ->
            // A 5.x database, with migrations still to run
            Flyway.configure().configuration(flyway.configuration).target(PARTIAL_TARGET).load().migrate()
            jdbc.update("UPDATE schema_version SET type = 'SPRING_JDBC' WHERE version = '11'")
            // Flyway alone refuses such a history
            assertFailsWith<FlywayException> { flyway.migrate() }

            startupStrategy().migrate(flyway)

            assertEquals("JDBC", jdbc.queryForObject("SELECT type FROM schema_version WHERE version = '11'", String::class.java))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM schema_version WHERE type = 'SPRING_JDBC'", Int::class.java))
            assertTrue(flyway.info().pending().isEmpty(), "Every migration has run")
            flyway.validate()
        }
    }

    @Test
    fun `A new database without a schema history is migrated`() {
        withDatabase { flyway, _ ->
            startupStrategy().migrate(flyway)

            assertTrue(flyway.info().pending().isEmpty(), "Every migration has run")
            flyway.validate()
        }
    }

    private fun startupStrategy() = StartupStrategy(GenericApplicationContext().apply { refresh() })

    private fun withDatabase(code: (flyway: Flyway, jdbc: JdbcTemplate) -> Unit) {
        val database = "ontrack_startup_${UUID.randomUUID().toString().replace("-", "")}"
        val databaseUrl = url.replace(Regex("/[^/?]+(\\?|$)"), "/$database$1")
        admin("CREATE DATABASE $database")
        try {
            val dataSource = DriverManagerDataSource(databaseUrl, username, password)
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                // As configured by FlywayConfiguration
                .table("schema_version")
                .load()
            code(flyway, JdbcTemplate(dataSource))
        } finally {
            admin("DROP DATABASE IF EXISTS $database WITH (FORCE)")
        }
    }

    private fun admin(sql: String) {
        DriverManager.getConnection(url, username, password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    companion object {
        /**
         * Version up to which the 5.x database is migrated: past `V11__476_ProjectEntityCreationMigration`,
         * with migrations still pending.
         */
        private const val PARTIAL_TARGET = "29"
    }
}
