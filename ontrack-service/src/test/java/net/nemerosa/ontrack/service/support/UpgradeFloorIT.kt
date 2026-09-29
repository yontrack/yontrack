package net.nemerosa.ontrack.service.support

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticApplicationContext
import java.sql.DriverManager
import java.util.*
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The upgrade floor of 6.0 (ADR 0018): a database which has never run 5.0.0 is refused before
 * Flyway touches it, and any other database starts.
 *
 * Each test runs [StartupStrategy] - the strategy Spring Boot hands its Flyway to - against a
 * database of its own, created for the test on the IT Postgres server and dropped afterwards.
 */
class UpgradeFloorIT {

    private val url = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/ontrack")
    private val username = System.getProperty("spring.datasource.username", "ontrack")
    private val password = System.getProperty("spring.datasource.password", "ontrack")

    @Test
    fun `An empty database starts and is migrated to the latest version`() {
        withDatabase { flyway ->
            startup(flyway)
            assertTrue(flyway.info().pending().isEmpty(), "Every migration has run")
        }
    }

    @Test
    fun `A database last migrated by 5_0_0 starts and is migrated to the latest version`() {
        withDatabase { flyway ->
            flyway(flyway, target = LAST_OF_5_0_0).migrate()

            startup(flyway)

            assertTrue(flyway.info().pending().isEmpty(), "Every migration has run")
        }
    }

    @Test
    fun `A database last migrated by 4_x is refused before Flyway runs`() {
        withDatabase { flyway ->
            flyway(flyway, target = LAST_OF_4_X).migrate()

            val exception = assertFailsWith<UpgradeFloorException> {
                startup(flyway)
            }

            assertContains(exception.message ?: "", "upgrade to any 5.x release first")
            assertEquals(
                MigrationVersion.fromVersion(LAST_OF_4_X),
                flyway.info().current().version,
                "Flyway has not run"
            )
        }
    }

    private fun startup(flyway: Flyway) {
        val context = StaticApplicationContext().apply { refresh() }
        StartupStrategy(context).migrate(flyway)
    }

    private fun flyway(flyway: Flyway, target: String): Flyway =
        Flyway.configure()
            .configuration(flyway.configuration)
            .target(target)
            .load()

    private fun withDatabase(code: (flyway: Flyway) -> Unit) {
        val database = "ontrack_floor_${UUID.randomUUID().toString().replace("-", "")}"
        val databaseUrl = url.replace(Regex("/[^/?]+(\\?|$)"), "/$database$1")
        admin("CREATE DATABASE $database")
        try {
            code(
                Flyway.configure()
                    .dataSource(databaseUrl, username, password)
                    // As configured by FlywayConfiguration
                    .table("schema_version")
                    .load()
            )
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
         * Last Flyway version of 5.0.0, `V68__V5_av_audit.sql`: the upgrade floor of 6.0.
         */
        private const val LAST_OF_5_0_0 = "68"

        /**
         * Last Flyway version of the 4.x line (4.13.26), `V56__1422_workflow_instance_no_status.sql`.
         */
        private const val LAST_OF_4_X = "56"
    }
}
