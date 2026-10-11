package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.OntrackConfigProperties.ContractMigrationsMode
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.service.support.TemporaryDatabase.Companion.withTemporaryDatabase
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.BadSqlGrammarException
import java.util.function.IntConsumer
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two-phase migrations (#2057): the expand migrations of `db/migration` run at startup, the contract
 * migrations of `db/contract` after them at startup, or later on, by the `run-contract-and-exit`
 * mode.
 *
 * The migrations of `src/test/resources/migration-it` come on top of the real ones: the expand
 * V100001 creates `IT_LEGACY` and `IT_OTHER`, which the contracts V100002 and V100004 drop.
 */
class TwoPhaseMigrationsIT {

    @Test
    fun `Startup mode runs the contract migrations after the expand ones, in version order`() {
        withDatabase { database, flyway ->
            val startup = RecordingStartupService()

            startupStrategy(ContractMigrationsMode.STARTUP, startupService = startup).migrate(flyway)

            assertTrue(flyway.info().pending().isEmpty(), "No expand migration is pending")
            assertEquals(listOf("100002", "100004"), appliedContracts(database))
            assertTableAbsent(database, "IT_LEGACY")
            assertTrue(startup.started, "The startup services have run")
        }
    }

    @Test
    fun `The Java migrations of the expand phase never run in the contract phase`() {
        withDatabase(javaMigration = true) { database, flyway ->
            startupStrategy(ContractMigrationsMode.STARTUP).migrate(flyway)

            assertEquals(1, database.jdbc.queryForObject("SELECT COUNT(*) FROM schema_version WHERE version = '100005'", Int::class.java))
            assertEquals(listOf("100002", "100004"), appliedContracts(database))
        }
    }

    @Test
    fun `Startup mode on an existing database without contract history creates it`() {
        withTemporaryDatabase("ontrack_two_phase") { database ->
            // A 6.0 database, before the contract phase existed
            val flyway = Flyway.configure().dataSource(database.dataSource).table("schema_version").load()
            flyway.migrate()

            startupStrategy(ContractMigrationsMode.STARTUP, contractLocations = listOf(ContractMigrations.LOCATION)).migrate(flyway)

            assertTrue(flyway.info().pending().isEmpty(), "No expand migration is pending")
            flyway.validate()
            assertTrue(ContractMigrations.flyway(flyway, listOf(ContractMigrations.LOCATION)).info().pending().isEmpty(), "No contract migration is pending")
            assertEquals(
                1,
                database.jdbc.queryForObject("SELECT COUNT(*) FROM schema_version_contract", Int::class.java),
                "The contract history only has its baseline",
            )
        }
    }

    @Test
    fun `Deferred mode leaves the contract migrations pending, the previous version reading on, until they run and exit`() {
        withDatabase { database, flyway ->
            val startup = RecordingStartupService()

            startupStrategy(ContractMigrationsMode.DEFERRED, startupService = startup).migrate(flyway)

            assertTrue(startup.started, "The startup services have run")
            assertTrue(flyway.info().pending().isEmpty(), "No expand migration is pending")
            assertEquals(listOf("100002", "100004"), pendingContracts(flyway, CONTRACT))
            // A query of the previous version against a table that a pending contract drops
            assertEquals("legacy", database.jdbc.queryForObject("SELECT NAME FROM IT_LEGACY", String::class.java))

            // The post-upgrade hook
            val exit = RecordingExit()
            val hookStartup = RecordingStartupService()
            startupStrategy(ContractMigrationsMode.DEFERRED, runContractAndExit = true, startupService = hookStartup, exit = exit).migrate(flyway)

            assertEquals(0, exit.code)
            assertFalse(hookStartup.started, "No startup service runs before exiting")
            assertEquals(listOf("100002", "100004"), appliedContracts(database))
            assertTableAbsent(database, "IT_LEGACY")
            assertFailsWith<BadSqlGrammarException> {
                database.jdbc.queryForObject("SELECT NAME FROM IT_LEGACY", String::class.java)
            }
        }
    }

    @Test
    fun `A failing contract migration exits with an error and leaves the history consistent`() {
        withDatabase { database, flyway ->
            startupStrategy(ContractMigrationsMode.DEFERRED, contractLocations = CONTRACT_FAILING).migrate(flyway)

            val exit = RecordingExit()
            startupStrategy(ContractMigrationsMode.DEFERRED, runContractAndExit = true, contractLocations = CONTRACT_FAILING, exit = exit).migrate(flyway)

            assertEquals(1, exit.code)
            // The contracts before the failing one are applied, the failing one is still pending
            assertEquals(listOf("100002"), appliedContracts(database))
            assertEquals(
                0,
                database.jdbc.queryForObject("SELECT COUNT(*) FROM schema_version_contract WHERE NOT success", Int::class.java),
                "No failed migration is recorded",
            )
            assertEquals(listOf("100004"), pendingContracts(flyway, CONTRACT_FAILING))
        }
    }

    @Test
    fun `Running the contract migrations with none pending exits without error`() {
        withDatabase { database, flyway ->
            startupStrategy(ContractMigrationsMode.STARTUP).migrate(flyway)

            val exit = RecordingExit()
            startupStrategy(ContractMigrationsMode.STARTUP, runContractAndExit = true, exit = exit).migrate(flyway)

            assertEquals(0, exit.code)
            assertEquals(listOf("100002", "100004"), appliedContracts(database))
        }
    }

    private fun startupStrategy(
        mode: ContractMigrationsMode,
        runContractAndExit: Boolean = false,
        contractLocations: List<String> = CONTRACT,
        startupService: StartupService = RecordingStartupService(),
        exit: RecordingExit = RecordingExit(),
    ) = StartupStrategy(
        GenericApplicationContext().apply {
            registerBean("recordingStartupService", StartupService::class.java, Supplier { startupService })
            refresh()
        },
        OntrackConfigProperties.DatabaseProperties().apply {
            contractMigrations = mode
            this.runContractAndExit = runContractAndExit
        },
        contractLocations,
        exit,
    )

    /**
     * Versions of the applied contract migrations, in the order they ran, without the baseline.
     */
    private fun appliedContracts(database: TemporaryDatabase): List<String> =
        database.jdbc.queryForList(
            "SELECT version FROM schema_version_contract WHERE success AND type <> 'BASELINE' ORDER BY installed_rank",
            String::class.java,
        ).filterNotNull()

    private fun pendingContracts(flyway: Flyway, locations: List<String>): List<String> =
        ContractMigrations.flyway(flyway, locations).info().pending().map { it.version.version }

    private fun assertTableAbsent(database: TemporaryDatabase, table: String) {
        assertNull(database.jdbc.queryForObject("SELECT TO_REGCLASS(?)::TEXT", String::class.java, table.lowercase()))
    }

    private fun withDatabase(javaMigration: Boolean = false, code: (database: TemporaryDatabase, flyway: Flyway) -> Unit) {
        withTemporaryDatabase("ontrack_two_phase") { database ->
            val flyway = Flyway.configure()
                .dataSource(database.dataSource)
                // As configured by FlywayConfiguration
                .table("schema_version")
                .configuration(mapOf(ContractMigrations.POSTGRESQL_TRANSACTIONAL_LOCK to "false"))
                .locations(ContractMigrations.EXPAND_LOCATION, "classpath:migration-it/expand")
                .apply {
                    // As Spring Boot passes the Java migrations which are beans
                    if (javaMigration) javaMigrations(V100005__ItJavaMigration())
                }
                .load()
            code(database, flyway)
        }
    }

    private class RecordingStartupService : StartupService {
        var started = false
        override fun getName() = "Recording"
        override fun startupOrder() = StartupService.SYSTEM
        override fun start() {
            started = true
        }
    }

    private class RecordingExit : IntConsumer {
        var code: Int? = null
        override fun accept(code: Int) {
            this.code = code
        }
    }

    @Suppress("ClassName")
    private class V100005__ItJavaMigration : BaseJavaMigration() {
        override fun migrate(context: Context) {}
    }

    companion object {
        private val CONTRACT = listOf("classpath:migration-it/contract")
        private val CONTRACT_FAILING = listOf("classpath:migration-it/contract-failing")
    }
}
