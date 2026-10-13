package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.service.support.TemporaryDatabase.Companion.withTemporaryDatabase
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Old Flyway versions recorded the Java migrations as `SPRING_JDBC`, a type the Flyway of Yontrack 6
 * no longer knows (#2050): [StartupStrategy] relabels them as `JDBC` before migrating.
 *
 * Several replicas starting together run their [StartupService]s one at a time (#2087).
 *
 * Each test runs against a [TemporaryDatabase].
 */
class StartupStrategyIT {

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

    @Test
    fun `The startup phase of a second context waits for the first one's lock`() {
        withDatabase { flyway, jdbc ->
            val events = ConcurrentLinkedQueue<String>()
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val first = TestStartupService("first") {
                events += "first started"
                firstStarted.countDown()
                releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                events += "first done"
            }
            val second = TestStartupService("second") {
                events += "second started"
            }

            val firstStartup = CompletableFuture.runAsync { startupStrategy(first).migrate(flyway) }
            assertTrue(firstStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The first context runs its startup services")

            val secondStartup = CompletableFuture.runAsync { startupStrategy(second).migrate(flyway) }
            awaitStartupLockWaiter(jdbc) {
                assertFalse("second started" in events, "The second context runs no startup service while the first one holds the lock")
            }
            assertFalse(secondStartup.isDone, "The second context is still starting")

            releaseFirst.countDown()
            firstStartup.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            secondStartup.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(listOf("first started", "first done", "second started"), events.toList())
        }
    }

    @Test
    fun `A failed startup phase releases the lock`() {
        withDatabase { flyway, _ ->
            val failing = TestStartupService("failing") { error("Startup failure") }
            val failure = assertFailsWith<ExecutionException> {
                CompletableFuture.runAsync { startupStrategy(failing).migrate(flyway) }
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            assertEquals("Startup failure", failure.cause?.message)

            var started = false
            CompletableFuture.runAsync { startupStrategy(TestStartupService("next") { started = true }).migrate(flyway) }
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(started, "The next context runs its startup services")
        }
    }

    /**
     * Waits until a session waits for the startup lock, checking [whileWaiting] on the way.
     */
    private fun awaitStartupLockWaiter(jdbc: JdbcTemplate, whileWaiting: () -> Unit) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (System.currentTimeMillis() < deadline) {
            whileWaiting()
            val waiters = jdbc.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM pg_locks
                    WHERE locktype = 'advisory'
                    AND NOT granted
                    AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
                    AND classid = ?
                    AND objsubid = 2
                """,
                Int::class.java,
                StartupStrategy.LOCK_CLASS.toLong(),
            )
            if (waiters != null && waiters > 0) {
                whileWaiting()
                return
            }
            Thread.sleep(100)
        }
        fail("No session waits for the startup lock")
    }

    private class TestStartupService(
        private val name: String,
        private val code: () -> Unit,
    ) : StartupService {
        override fun getName(): String = name
        override fun startupOrder(): Int = StartupService.SYSTEM
        override fun start() = code()
    }

    private fun startupStrategy(vararg startupServices: StartupService) = StartupStrategy(
        GenericApplicationContext().apply {
            startupServices.forEach { service ->
                beanFactory.registerSingleton(service.name, service)
            }
            refresh()
        },
        OntrackConfigProperties.DatabaseProperties(),
        listOf(ContractMigrations.LOCATION),
    ) { error("No exit at startup") }

    private fun withDatabase(code: (flyway: Flyway, jdbc: JdbcTemplate) -> Unit) {
        withTemporaryDatabase("ontrack_startup") { database ->
            val flyway = Flyway.configure()
                .dataSource(database.dataSource)
                // As configured by FlywayConfiguration
                .table("schema_version")
                .load()
            code(flyway, database.jdbc)
        }
    }

    companion object {
        /**
         * Version up to which the 5.x database is migrated: past `V11__476_ProjectEntityCreationMigration`,
         * with migrations still pending.
         */
        private const val PARTIAL_TARGET = "29"

        private const val TIMEOUT_SECONDS = 60L
    }
}
