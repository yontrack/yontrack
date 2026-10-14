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
 * build when its commit is set, changed or removed: the commit property of the build is the only input
 * of the computation which belongs to the build, and every change of a property posts an event.
 *
 * The creation of a build queues nothing: either its commit is not set yet, and there is nothing to
 * compute, or it is set in the same transaction, which queues the computation (#2093). Setting the same
 * commit again queues a computation, which is the way to retry after an SCM error. A build in a
 * project without SCM is computed too, as `UNKNOWN`, once it has a commit.
 *
 * The computation calls the SCM, so it never runs inside the transaction which sets the commit, nor
 * makes it wait: it is queued after the commit of this transaction, once per build whatever the number
 * of changes in the transaction, and runs on any replica, one at a time per build (see
 * [AssistedChangeQueueProcessor]). Its failures are logged, and an SCM error is stored as an `UNKNOWN`
 * value, computed again on the next trigger.
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
        if (event.eventType == EventFactory.PROPERTY_CHANGE || event.eventType == EventFactory.PROPERTY_DELETE) {
            val build = event.entities[ProjectEntityType.BUILD] as? Build
            val property = event.values["PROPERTY"]?.value
            if (build != null && property != null && property in commitPropertyTypes) {
                onBuild(build)
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
