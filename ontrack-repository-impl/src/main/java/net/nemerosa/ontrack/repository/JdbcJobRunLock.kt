package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.job.JobRunLockHandle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/**
 * Guards the runs of the jobs across the instances of Yontrack with a Postgres advisory lock,
 * keyed on the job key.
 *
 * The run of a job is not one transaction: the lock is a session-level one, held by a connection
 * of its own, taken from the pool outside any transaction of the caller and kept for the whole
 * run. Releasing the lock unlocks it and returns the connection to the pool.
 */
@Component
class JdbcJobRunLock(
    private val dataSource: DataSource,
) : JobRunLock {

    private val logger = LoggerFactory.getLogger(JdbcJobRunLock::class.java)

    override fun tryLock(key: JobKey): JobRunLockHandle? {
        val lockKey = lockKey(key)
        // Straight from the pool, not bound to any transaction of the current thread
        val connection = dataSource.connection
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
    }
}
