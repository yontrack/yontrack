package net.nemerosa.ontrack.extension.scm.changelog.assistants

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.scm.service.SCMBuildCommitPropertyType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Computes the [assisted change][AssistedChangeService.computeAssistedChange] of a build when it is
 * created, and when its commit is set, which usually happens after its creation.
 *
 * The computation calls the SCM, so it never runs inside the transaction which creates the build, nor
 * makes it wait: it runs in the background, after the commit of this transaction. Its failures are
 * logged, and an SCM error is stored as an `UNKNOWN` value, computed again on the next trigger.
 *
 * The computations of a given build run one at a time, in the order of their triggers.
 */
@Component
class AssistedChangeEventListener(
    private val assistedChangeService: AssistedChangeService,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
) : EventListener {

    private val logger: Logger = LoggerFactory.getLogger(AssistedChangeEventListener::class.java)

    /**
     * Lanes of computation: a build always goes to the same lane, where the computations run one at
     * a time.
     */
    private val lanes = List(LANES) { Dispatchers.IO.limitedParallelism(1) }

    private val scope = CoroutineScope(SupervisorJob())

    /**
     * Computations in progress
     */
    private val running: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    /**
     * Names of the property types holding the commit of a build
     */
    private val commitPropertyTypes: Set<String> by lazy {
        propertyService.propertyTypes
            .filter { it is SCMBuildCommitPropertyType }
            .map { it.typeName }
            .toSet()
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_BUILD -> onBuild(event.getEntity(ProjectEntityType.BUILD))
            EventFactory.PROPERTY_CHANGE -> {
                val build = event.entities[ProjectEntityType.BUILD] as? Build
                val property = event.values["PROPERTY"]?.value
                if (build != null && property != null && property in commitPropertyTypes) {
                    onBuild(build)
                }
            }
        }
    }

    private fun onBuild(build: Build) {
        val buildId = build.id
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    launch(buildId)
                }
            })
        } else {
            launch(buildId)
        }
    }

    private fun launch(buildId: ID) {
        val lane = lanes[buildId.value % LANES]
        val job = scope.launch(lane, start = CoroutineStart.LAZY) {
            try {
                securityService.asAdmin {
                    structureService.findBuildByID(buildId)?.let { build ->
                        assistedChangeService.computeAssistedChange(build)
                    }
                }
            } catch (any: Exception) {
                logger.error("Cannot compute the assisted change of build $buildId", any)
            }
        }
        running += job
        job.invokeOnCompletion { running -= job }
        job.start()
    }

    /**
     * Waits for the computations in progress to complete. For tests.
     */
    fun awaitCompletion() {
        runBlocking {
            while (running.isNotEmpty()) {
                running.toList().joinAll()
            }
        }
    }

    companion object {
        /**
         * Number of lanes of computation
         */
        private const val LANES = 4
    }
}
