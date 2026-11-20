package net.nemerosa.ontrack.extension.av.audit

import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AutoVersioningAuditQueryServiceImpl(
    private val store: AutoVersioningAuditStore,
    private val securityService: SecurityService,
    private val structureService: StructureService,
) : AutoVersioningAuditQueryService {

    override fun findByUUID(branch: Branch, uuid: String): AutoVersioningAuditEntry? =
        if (securityService.isProjectFunctionGranted(branch, ProjectView::class.java)) {
            store.findByUUID(branch, uuid)
        } else {
            null
        }

    override fun findByFilter(filter: AutoVersioningAuditQueryFilter): List<AutoVersioningAuditEntry> =
        store.auditVersioningEntries(filter, visibleProjectIds())

    override fun countByFilter(filter: AutoVersioningAuditQueryFilter): Int =
        store.auditVersioningEntriesCount(filter, visibleProjectIds())

    /**
     * IDs of the projects whose orders the current user can see, `null` when `ProjectView` is
     * granted on every project (no restriction).
     */
    private fun visibleProjectIds(): List<Int>? = when {
        securityService.isProjectFunctionGranted(ANY_PROJECT_ID, ProjectView::class.java) -> null
        securityService.isLogged -> structureService.projectList.map { it.id() }
        else -> emptyList()
    }

    companion object {
        /**
         * Not the ID of any project: a function granted on it is granted on every project.
         */
        private const val ANY_PROJECT_ID = 0
    }
}
