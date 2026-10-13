package net.nemerosa.ontrack.extension.av.queue

import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Serialises the processing of the auto-versioning orders sharing a routing identifier (source
 * project, target project, target branch, target paths), across the instances of Yontrack.
 *
 * One consumer per queue used to be enough, but two instances run side by side during a rolling
 * upgrade, each with a consumer on every queue.
 *
 * Orders whose paths differ are processed concurrently, so their merges into the same target
 * branch are serialised by a lock of their own, see [withMergeLock].
 *
 * The locks are Postgres advisory locks, each held by a transaction of its own on a dedicated
 * connection, so that it neither joins nor holds the transactions of the processing — whose
 * failure is recorded after the processing transaction was rolled back.
 */
@Component
class AutoVersioningProcessingLock(
    private val dataSource: DataSource,
) {

    /**
     * Runs [code] while holding the lock on the [routingIdentifier], waiting for it if needed.
     */
    fun <T> withLock(routingIdentifier: String, code: () -> T): T =
        withAdvisoryLock(LOCK_CLASS, routingIdentifier, code)

    /**
     * Runs [code] while holding the lock on the merges into the [branch] of the [repository],
     * waiting for it if needed.
     *
     * Taken last, within the lock on the routing identifier, and released at once: it cannot be
     * part of a deadlock.
     */
    fun <T> withMergeLock(repository: String, branch: String, code: () -> T): T =
        withAdvisoryLock(MERGE_LOCK_CLASS, "$repository#$branch", code)

    private fun <T> withAdvisoryLock(lockClass: Int, key: String, code: () -> T): T =
        dataSource.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))").use { statement ->
                    statement.setInt(1, lockClass)
                    statement.setString(2, key)
                    // pg_advisory_xact_lock returns void: the row is read and ignored
                    statement.executeQuery().close()
                }
                code()
            } finally {
                // Ending the transaction releases the lock
                try {
                    connection.rollback()
                } finally {
                    connection.autoCommit = autoCommit
                }
            }
        }

    companion object {
        /**
         * First key of the advisory locks on the processing of the auto-versioning orders, the
         * second being a hash of their routing identifier: "AVQU" in ASCII — next to the locks on
         * the trails ("TRAI"), on the blobs of the evidence ("EVID") and on the runs of the jobs
         * ("JOBR").
         */
        const val LOCK_CLASS: Int = 0x41565155

        /**
         * First key of the advisory locks on the merges into a target branch, the second being
         * a hash of the repository and of the branch: "AVMG" in ASCII.
         */
        const val MERGE_LOCK_CLASS: Int = 0x41564D47
    }
}
