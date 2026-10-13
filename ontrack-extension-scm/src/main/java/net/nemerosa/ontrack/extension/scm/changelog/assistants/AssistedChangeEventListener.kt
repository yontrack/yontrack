package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.queue.dispatching.QueuePostCommitDispatcher
import net.nemerosa.ontrack.extension.scm.service.SCMBuildCommitPropertyType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * Queues the computation of the [assisted change][AssistedChangeService.computeAssistedChange] of a
 * build when it is created, and when its commit is set, which usually happens after its creation.
 *
 * The computation calls the SCM, so it never runs inside the transaction which creates the build, nor
 * makes it wait: it is queued after the commit of this transaction, and runs on any replica, one at a
 * time per build (see [AssistedChangeQueueProcessor]). Its failures are logged, and an SCM error is
 * stored as an `UNKNOWN` value, computed again on the next trigger.
 */
@Component
class AssistedChangeEventListener(
    private val propertyService: PropertyService,
    private val queuePostCommitDispatcher: QueuePostCommitDispatcher,
    private val assistedChangeQueueProcessor: AssistedChangeQueueProcessor,
) : EventListener {

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
        queuePostCommitDispatcher.dispatchAfterCommit(
            assistedChangeQueueProcessor,
            AssistedChangeQueuePayload(buildId = build.id()),
        )
    }
}
