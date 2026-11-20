package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.model.security.*
import org.springframework.stereotype.Component

/**
 * The authorizations of an auto-versioning audit entry, for the UI to know what to offer:
 *
 * - `auto_versioning_audit_entry/reschedule` — who may reschedule its order: [ProjectView] and
 *   [AutoVersioningReschedule] on the **target** project of the order, as checked by the rescheduling.
 */
@Component
class AutoVersioningAuditEntryAuthorizationContributor(
    private val securityService: SecurityService,
) : AuthorizationContributor {

    companion object {
        /**
         * Name of the authorizations of the auto-versioning audit entries
         */
        const val AUTO_VERSIONING_AUDIT_ENTRY = "auto_versioning_audit_entry"

        /**
         * Rescheduling the order of an entry
         */
        const val RESCHEDULE = "reschedule"
    }

    override fun appliesTo(context: Any): Boolean = context is AutoVersioningAuditEntry

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> {
        val target = (context as AutoVersioningAuditEntry).order.branch
        return listOf(
            Authorization(
                name = AUTO_VERSIONING_AUDIT_ENTRY,
                action = RESCHEDULE,
                authorized = securityService.isProjectFunctionGranted(target, ProjectView::class.java) &&
                        securityService.isProjectFunctionGranted(target, AutoVersioningReschedule::class.java),
            )
        )
    }
}
