package net.nemerosa.ontrack.repository

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.job.JobRunLockHandle
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/**
 * Guards the runs of the jobs across the instances of Yontrack with a Postgres advisory lock,
 * keyed on the job key.
 *
 * The run of a job is not one transaction: the lock is a session-level one, held by a connection
 * of its own, taken outside any transaction of the caller and kept for the whole run. Releasing the
 * lock unlocks it and returns the connection to its pool.
 *
 * These connections come from a pool of their own (#2067), copied from the main pool: held for
 * the whole run of a job, they would otherwise be reported by the leak detection of the main pool
 * for any run longer than its threshold, and take connections from the work of the application.
 */
@Component
class JdbcJobRunLock(
    private val dataSource: DataSource,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : JobRunLock, DisposableBean {

    private val logger = LoggerFactory.getLogger(JdbcJobRunLock::class.java)

    private val closed = AtomicBoolean(false)

    private val lockPoolDelegate = lazy { createLockPool() }

    /**
     * Pool of the connections holding the locks, opened on the first lock. The main data source
     * when it is not a Hikari pool.
     */
    internal val lockPool: DataSource by lockPoolDelegate

    private fun createLockPool(): DataSource {
        val main = if (dataSource.isWrapperFor(HikariDataSource::class.java)) {
            dataSource.unwrap(HikariDataSource::class.java)
        } else {
            logger.warn("[job][lock] The main data source is not a Hikari pool: the run locks take their connections from it")
            return dataSource
        }
        val config = HikariConfig()
        main.copyStateTo(config)
        config.poolName = "${config.poolName ?: "HikariPool"}-job-run-locks"
        // A connection is held for the whole run of a job, which is not a leak
        config.leakDetectionThreshold = 0
        // Opened on demand: most of the time, no job is running
        config.minimumIdle = 0
        config.maximumPoolSize = ontrackConfigProperties.jobs.poolSize + LOCK_POOL_MARGIN
        logger.info("[job][lock] Pool of the run locks: {} connections at most", config.maximumPoolSize)
        return HikariDataSource(config)
    }

    override fun destroy() {
        // No pool opened once closed
        closed.set(true)
        if (lockPoolDelegate.isInitialized()) {
            // Ends the sessions, and so releases any lock still held
            (lockPool as? HikariDataSource)?.takeIf { it !== dataSource }?.close()
        }
    }

    override fun tryLock(key: JobKey): JobRunLockHandle? {
        if (closed.get()) {
            throw SQLException("The run locks of the jobs are closed")
        }
        val lockKey = lockKey(key)
        // From the pool of the locks, not bound to any transaction of the current thread
        val connection = lockPool.connection
        try {
            // The lock is held by the session, not by a transaction
            connection.autoCommit = true
            val acquired = connection.prepareStatement("SELECT pg_try_advisory_lock(?, hashtext(?))").use { statement ->
                statement.setInt(1, LOCK_CLASS)
                statement.setString(2, lockKey)
                statement.executeQuery().use { rs ->
                    rs.next() && rs.getBoolean(1)
                }
            }
            return if (acquired) {
                Handle(connection, lockKey)
            } else {
                connection.close()
                null
            }
        } catch (any: Exception) {
            try {
                connection.close()
            } catch (closing: Exception) {
                any.addSuppressed(closing)
            }
            throw any
        }
    }

    private inner class Handle(
        private val connection: Connection,
        private val lockKey: String,
    ) : JobRunLockHandle {

        private val released = AtomicBoolean(false)

        override fun release() {
            if (released.compareAndSet(false, true)) {
                connection.use {
                    val unlocked = try {
                        it.prepareStatement("SELECT pg_advisory_unlock(?, hashtext(?))").use { statement ->
                            statement.setInt(1, LOCK_CLASS)
                            statement.setString(2, lockKey)
                            statement.executeQuery().use { rs ->
                                rs.next() && rs.getBoolean(1)
                            }
                        }
                    } catch (any: Exception) {
                        // Back in the pool, the connection would keep the lock: ending its session
                        // releases it instead
                        try {
                            it.abort(Runnable::run)
                        } catch (aborting: Exception) {
                            any.addSuppressed(aborting)
                        }
                        throw any
                    }
                    if (!unlocked) {
                        logger.warn("[job][lock]{} The run lock was not held when releasing it", lockKey)
                    }
                }
            }
        }
    }

    companion object {
        /**
         * First key of the advisory locks on the runs of the jobs, the second being a hash of the
         * job key: "JOBR" in ASCII — next to the locks on the trails ("TRAI"), on the blobs of the
         * evidence ("EVID") and on the processing of the auto-versioning orders ("AVQU").
         */
        const val LOCK_CLASS: Int = 0x4A4F4252

        /**
         * Key of the lock of a job: the string form of the job key only, which is made of the keys
         * of its category, its type and its ID, so that it is the same across instances and
         * versions.
         */
        fun lockKey(key: JobKey): String = key.toString()

        /**
         * Connections of the pool of the locks on top of the threads of the jobs, for the locks
         * taken outside of them: the rebuilds of the search documents at startup, one per indexer.
         */
        const val LOCK_POOL_MARGIN: Int = 10
    }
}
