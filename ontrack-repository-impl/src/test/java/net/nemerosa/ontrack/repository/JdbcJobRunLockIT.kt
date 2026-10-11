package net.nemerosa.ontrack.repository

import com.zaxxer.hikari.HikariDataSource
import net.nemerosa.ontrack.it.AbstractITTestSupport
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.*

class JdbcJobRunLockIT : AbstractITTestSupport() {

    @Autowired
    private lateinit var jobRunLock: JobRunLock

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

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
    fun `The connections of the locks are returned to the pool`() {
        val pool = dataSource.unwrap(HikariDataSource::class.java)
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
        // Other threads of the application may borrow connections meanwhile
        val deadline = System.currentTimeMillis() + 10_000
        while (pool.hikariPoolMXBean.activeConnections > baseline && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertTrue(
            pool.hikariPoolMXBean.activeConnections <= baseline,
            "Pool active count back to baseline"
        )
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
