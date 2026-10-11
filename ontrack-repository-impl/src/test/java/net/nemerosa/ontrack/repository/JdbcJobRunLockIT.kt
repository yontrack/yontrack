package net.nemerosa.ontrack.repository

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.nemerosa.ontrack.it.AbstractITTestSupport
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import kotlin.test.*

class JdbcJobRunLockIT : AbstractITTestSupport() {

    @Autowired
    private lateinit var jobRunLock: JobRunLock

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var ontrackConfigProperties: OntrackConfigProperties

    private fun jobKey() = JobCategory.of(uid("jc-")).getType(uid("jt-")).getKey(uid("jk-"))

    @Test
    fun `The lock of a job is held until released`() {
        val key = jobKey()
        val handle = assertNotNull(jobRunLock.tryLock(key), "Lock acquired")
        try {
            assertNull(jobRunLock.tryLock(key), "Lock held elsewhere")
            assertNull(jobRunLock.tryLock(JobKey(key.type, key.id)), "Lock keyed on the string form of the job key")
            // Another job is not blocked
            val other = assertNotNull(jobRunLock.tryLock(jobKey()), "Another job is not locked")
            other.release()
        } finally {
            handle.release()
        }
        // Free again
        val again = assertNotNull(jobRunLock.tryLock(key), "Lock acquired again once released")
        again.release()
    }

    @Test
    fun `The lock is held by a connection outside the transaction of the caller`() {
        val key = jobKey()
        val tx = TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        val handle = tx.execute { status ->
            val handle = assertNotNull(jobRunLock.tryLock(key), "Lock acquired")
            // The session of the transaction does not hold the lock
            val txPid = namedParameterJdbcTemplate.queryForObject(
                "SELECT pg_backend_pid()",
                emptyMap<String, Any>(),
                Int::class.java
            )
            val holders = lockHolders(key)
            assertEquals(1, holders.size, "One session holds the lock")
            assertNotEquals(txPid, holders.single(), "The lock is not held by the session of the transaction")
            // Rolling the transaction back...
            status.setRollbackOnly()
            handle
        }!!
        try {
            // ... does not release the lock
            assertNull(jobRunLock.tryLock(key), "Lock still held after the transaction of the caller has ended")
        } finally {
            handle.release()
        }
        assertTrue(lockHolders(key).isEmpty(), "Lock released")
    }

    @Test
    fun `The locks are held by connections of a pool of their own`() {
        val main = dataSource.unwrap(HikariDataSource::class.java)
        val handle = assertNotNull(jobRunLock.tryLock(jobKey()), "Lock acquired")
        try {
            val pool = (jobRunLock as JdbcJobRunLock).lockPool.unwrap(HikariDataSource::class.java)
            assertNotSame(main, pool, "Not the main pool")
            assertNotEquals(main.poolName, pool.poolName, "Pool of its own")
            assertTrue(pool.hikariPoolMXBean.activeConnections >= 1, "The lock holds a connection of its own pool")
            assertEquals(
                ontrackConfigProperties.jobs.poolSize + JdbcJobRunLock.LOCK_POOL_MARGIN,
                pool.maximumPoolSize,
                "Pool sized on the number of job threads"
            )
        } finally {
            handle.release()
        }
    }

    @Test
    fun `A lock held longer than the leak detection threshold of the main pool is not reported as a leak`() {
        // A main pool with the shortest leak detection threshold Hikari allows
        val config = HikariConfig()
        dataSource.unwrap(HikariDataSource::class.java).copyStateTo(config)
        config.poolName = uid("main-")
        config.leakDetectionThreshold = 2_000
        config.minimumIdle = 0
        config.maximumPoolSize = 2
        HikariDataSource(config).use { main ->
            val lock = JdbcJobRunLock(main, ontrackConfigProperties)
            try {
                val warnings = captureLeakWarnings {
                    assertNotNull(lock.tryLock(jobKey()), "Lock acquired").release()
                    assertEquals(
                        0,
                        lock.lockPool.unwrap(HikariDataSource::class.java).leakDetectionThreshold,
                        "No leak detection on the connections of the locks"
                    )
                    val handle = assertNotNull(lock.tryLock(jobKey()), "Lock acquired")
                    try {
                        // Longer than the threshold, plus the period of the leak detection task
                        Thread.sleep(3_000)
                    } finally {
                        handle.release()
                    }
                }
                assertTrue(warnings.isEmpty(), "No leak reported: $warnings")
            } finally {
                lock.destroy()
            }
        }
    }

    @Test
    fun `The connections of the locks are returned to the pool`() {
        // Opens the pool of the locks
        assertNotNull(jobRunLock.tryLock(jobKey()), "Lock acquired").release()
        val pool = (jobRunLock as JdbcJobRunLock).lockPool.unwrap(HikariDataSource::class.java)
        val baseline = pool.hikariPoolMXBean.activeConnections
        // More locks than the pool has connections: any leak exhausts the pool
        repeat(pool.maximumPoolSize * 2) {
            val key = jobKey()
            val handle = assertNotNull(jobRunLock.tryLock(key), "Lock acquired")
            try {
                // Lock held elsewhere: the connection of the attempt is returned at once
                assertNull(jobRunLock.tryLock(key), "Lock held elsewhere")
            } finally {
                handle.release()
            }
            // Releasing twice is harmless
            handle.release()
        }
        // Other jobs of the application may hold locks meanwhile
        val deadline = System.currentTimeMillis() + 10_000
        while (pool.hikariPoolMXBean.activeConnections > baseline && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertTrue(
            pool.hikariPoolMXBean.activeConnections <= baseline,
            "Pool active count back to baseline"
        )
    }

    @Test
    fun `No lock is taken once closed`() {
        val lock = JdbcJobRunLock(dataSource, ontrackConfigProperties)
        lock.destroy()
        assertFailsWith<SQLException> { lock.tryLock(jobKey()) }
    }

    private fun captureLeakWarnings(code: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger("com.zaxxer.hikari.pool.ProxyLeakTask") as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            code()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

    /**
     * PIDs of the sessions holding the lock of the job
     */
    private fun lockHolders(key: JobKey): List<Int?> =
        namedParameterJdbcTemplate.queryForList(
            """
                SELECT pid FROM pg_locks
                WHERE locktype = 'advisory'
                AND granted
                AND objsubid = 2
                AND classid::bigint = :lockClass
                AND objid::bigint = (hashtext(:lockKey)::bigint & 4294967295)
            """,
            mapOf(
                "lockClass" to JdbcJobRunLock.LOCK_CLASS,
                "lockKey" to JdbcJobRunLock.lockKey(key),
            ),
            Int::class.java
        )

}
