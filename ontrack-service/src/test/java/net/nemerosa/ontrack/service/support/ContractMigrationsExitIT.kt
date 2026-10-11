package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.service.support.TemporaryDatabase.Companion.withTemporaryDatabase
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ontrack.config.database.run-contract-and-exit` (#2057), in a process of its own: the application
 * runs the pending migrations and its process exits, with 0, or with 1 when a contract migration
 * fails.
 */
class ContractMigrationsExitIT {

    @Test
    fun `The process applies the contract migrations in version order and exits with 0`() {
        withTemporaryDatabase("ontrack_contract_exit") { database ->
            val code = runContractAndExit(database, "classpath:migration-it/contract")

            assertEquals(0, code)
            assertEquals(
                listOf("100002", "100004"),
                database.jdbc.queryForList(
                    "SELECT version FROM schema_version_contract WHERE success AND type <> 'BASELINE' ORDER BY installed_rank",
                    String::class.java,
                ),
            )
        }
    }

    @Test
    fun `The process exits with 1 when a contract migration fails`() {
        withTemporaryDatabase("ontrack_contract_exit") { database ->
            val code = runContractAndExit(database, "classpath:migration-it/contract-failing")

            assertEquals(1, code)
            assertEquals(
                listOf("100002"),
                database.jdbc.queryForList(
                    "SELECT version FROM schema_version_contract WHERE type <> 'BASELINE' ORDER BY installed_rank",
                    String::class.java,
                ),
                "The failing contract migration is not recorded",
            )
        }
    }

    private fun runContractAndExit(database: TemporaryDatabase, contractLocations: String): Int {
        val java = ProcessHandle.current().info().command().orElseThrow()
        val log = File.createTempFile("contract-exit", ".log")
        val process = ProcessBuilder(
            java,
            "-cp", System.getProperty("java.class.path"),
            "-Dit.contract.locations=$contractLocations",
            ContractPhaseTestApplication::class.java.name,
            "--spring.datasource.url=${database.url}",
            "--spring.datasource.username=${database.username}",
            "--spring.datasource.password=${database.password}",
            "--spring.flyway.locations=${ContractMigrations.EXPAND_LOCATION},classpath:migration-it/expand",
        )
            .redirectErrorStream(true)
            .redirectOutput(log)
            .apply {
                // As the Helm hook sets it, through the environment
                environment()["ONTRACK_CONFIG_DATABASE_RUNCONTRACTANDEXIT"] = "true"
            }
            .start()
        try {
            assertTrue(process.waitFor(2, TimeUnit.MINUTES), "The process has exited:\n${log.readText()}")
            println(log.readText())
            return process.exitValue()
        } finally {
            process.destroyForcibly()
            log.delete()
        }
    }
}
