package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.queue.QueueAckMode
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Computes the [assisted change][AssistedChangeService.computeAssistedChange] of a build, on any
 * replica (#2083).
 *
 * Acked at the end of the processing, so that a message in hand when its replica stops is
 * redelivered to another one: the computation reads the current state of the build, and running it
 * again is harmless.
 *
 * The computations of a build run one at a time, across the replicas, under an advisory lock on the
 * build: a computation reading a former commit and writing its value last would otherwise win over
 * the computation of the current commit.
 */
@Component
class AssistedChangeQueueProcessor(
    private val assistedChangeService: AssistedChangeService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val dataSource: DataSource,
) : QueueProcessor<AssistedChangeQueuePayload> {

    override val id: String = "assisted-change"

    override val payloadType: KClass<AssistedChangeQueuePayload> = AssistedChangeQueuePayload::class

    override val ackMode: QueueAckMode = QueueAckMode.END

    /**
     * Four queues, so that four builds are computed in parallel on each replica, while a build
     * always goes to the same queue.
     */
    override val defaultScale: Int = 4

    override fun isCancelled(payload: AssistedChangeQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: AssistedChangeQueuePayload): String = payload.buildId.toString()

    override fun process(payload: AssistedChangeQueuePayload, queueMetadata: QueueMetadata?) {
        withLock(payload.buildId) {
            securityService.asAdmin {
                // Read after the lock is taken: the build may have changed, or be gone
                structureService.findBuildByID(ID.of(payload.buildId))?.let { build ->
                    assistedChangeService.computeAssistedChange(build)
                }
            }
        }
    }

    /**
     * Runs [code] while holding the advisory lock on the build, waiting for it if needed.
     *
     * The lock is held by a transaction of its own on a dedicated connection, so that it neither
     * joins nor holds the transactions of the computation.
     */
    private fun withLock(buildId: Int, code: () -> Unit) {
        dataSource.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)").use { statement ->
                    statement.setInt(1, LOCK_CLASS)
                    statement.setInt(2, buildId)
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
    }

    companion object {
        /**
         * First key of the advisory locks on the computation of the assisted change of a build, the
         * second being the ID of the build: "ASCH" in ASCII — next to the locks on the trails
         * ("TRAI"), on the blobs of the evidence ("EVID"), on the runs of the jobs ("JOBR"), on the
         * processing of the auto-versioning orders ("AVQU", "AVMG"), on the startup ("STRT") and on
         * the re-indexation of the renamed entities ("SRNM").
         */
        const val LOCK_CLASS: Int = 0x41534348
    }
}
