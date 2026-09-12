package net.nemerosa.ontrack.extension.workflows.acl

import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class WorkflowInstanceAccessServiceImpl(
    private val securityService: SecurityService,
    private val structureService: StructureService,
) : WorkflowInstanceAccessService {

    override fun isWorkflowInstanceAccessible(instance: WorkflowInstance): Boolean {
        // WorkflowAudit is an override, not a fallback: it also covers the instances which resolve
        // to no project at all.
        if (securityService.isGlobalFunctionGranted(WorkflowAudit::class.java)) {
            return true
        }
        val project = findOwningProject(instance.event) ?: return false
        return securityService.isProjectFunctionGranted(project.id(), ProjectView::class.java)
    }

    override fun checkWorkflowInstanceAccess(instance: WorkflowInstance) {
        if (!isWorkflowInstanceAccessible(instance)) {
            throw AccessDeniedException("Workflow instance is not accessible.")
        }
    }

    /**
     * The project a workflow instance belongs to, derived from its event, or `null` when no project
     * can be derived.
     *
     * The resolution runs as admin and the [ProjectView] check is then made explicitly by the
     * caller. Letting the `find*ByID` variants do the filtering on their own would work today but
     * would make this the kind of *incidentally* protected code path this whole change exists to
     * remove.
     *
     * Deliberately **not** routed through `SerializableEventService.hydrate`: that one resolves the
     * ids with the `find*ByID` variants and puts their results, `null` included, straight into a
     * `Map<ProjectEntityType, ProjectEntity>`. Instances live 14 days and routinely outlive the
     * builds and branches they name, so hydrating a stale event is a latent crash rather than a
     * clean miss.
     *
     * `extraEntities` is ignored on purpose: narrower is safer, and an event naming a second project
     * in its extras would otherwise *widen* access on the strength of secondary context.
     */
    private fun findOwningProject(event: SerializableEvent): Project? =
        securityService.asAdmin {
            // The common case: both production paths (a notification on an entity, a slot pipeline
            // workflow) go through `withBuild`, which puts BUILD *and* BRANCH *and* PROJECT in the
            // map.
            event.entities[ProjectEntityType.PROJECT]
                ?.let { structureService.findProjectByID(ID.of(it)) }
            // Otherwise the most specific entity the event names, walked up to its project - which
            // keeps a hand-built event carrying a BUILD but no PROJECT readable.
                ?: MOST_SPECIFIC_FIRST.firstNotNullOfOrNull { type ->
                    event.entities[type]
                        ?.let { type.getFindEntityFn(structureService).apply(ID.of(it)) }
                        ?.project
                }
        }

    companion object {
        /**
         * Entity types from the most specific to the least, minus [ProjectEntityType.PROJECT] which
         * is looked up first anyway.
         */
        private val MOST_SPECIFIC_FIRST: List<ProjectEntityType> =
            ProjectEntityType.values()
                .filter { it != ProjectEntityType.PROJECT }
                .reversed()
    }
}
