package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.support.*
import org.springframework.stereotype.Component

/**
 * Collects the statuses of the connectors, one cluster job per [indicator][ConnectorStatusIndicator],
 * every 5 minutes, and stores them in `STORAGE`, from which every replica serves them (#2078): the
 * status of a connector seen from one replica stands for all of them.
 */
@Component
class ConnectorStatusJob(
    private val connectorStatusIndicators: List<ConnectorStatusIndicator>,
    private val storageService: StorageService,
) : JobOrchestratorSupplier, ConnectorGlobalStatusService {

    companion object {
        val CONNECTOR_STATUS_JOB = JobCategory.CORE.getType("connector-status").withName("Connector status collection")

        /**
         * Store of the statuses, by type of indicator
         */
        private const val STORE = "net.nemerosa.ontrack.service.support.ConnectorStatusJob"
    }

    override val globalStatus: ConnectorGlobalStatus
        get() = ConnectorGlobalStatus(
            connectorStatusIndicators.flatMap { statuses(it.type) ?: emptyList() }.sortedBy {
                it.status.description.connector
            }
        )

    /**
     * Last statuses collected for a type of indicator, `null` when never collected
     */
    fun statuses(type: String): List<CollectedConnectorStatus>? =
        storageService.find(STORE, type, StoredConnectorStatuses::class)?.statuses

    override fun collect() {
        connectorStatusIndicators.forEach { collect(it) }
    }

    private fun collect(connectorStatusIndicator: ConnectorStatusIndicator) {
        storageService.store(
            STORE,
            connectorStatusIndicator.type,
            StoredConnectorStatuses(connectorStatusIndicator.statuses.map { it.collected() }),
        )
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() = connectorStatusIndicators.map {
            createJob(it)
        }.map {
            JobRegistration.of(it).withSchedule(Schedule.everyMinutes(5))
        }

    private fun createJob(connectorStatusIndicator: ConnectorStatusIndicator) = object : Job {

        override fun getKey(): JobKey =
            CONNECTOR_STATUS_JOB.getKey(connectorStatusIndicator.type)

        override fun getTask() = JobRun {
            collect(connectorStatusIndicator)
        }

        override fun getDescription(): String =
            "Collection of connector statuses for type ${connectorStatusIndicator.type}"

        override fun isDisabled(): Boolean = false

    }

    /**
     * Statuses of the connectors of one type of indicator, as stored
     */
    data class StoredConnectorStatuses(
        val statuses: List<CollectedConnectorStatus>,
    )

}
