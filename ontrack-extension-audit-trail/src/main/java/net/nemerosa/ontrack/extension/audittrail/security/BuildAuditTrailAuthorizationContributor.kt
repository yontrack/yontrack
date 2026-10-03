package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Component

/**
 * `auditTrail/view` on a build, for the UI to know whether to offer the audit trail page of the
 * build: the user can see the build, and the build has a trail to read — the licence is on, or
 * entries were written for it before the licence lapsed ([TrailService.isTrailAvailable]). The
 * licence is folded in here, so that the UI does not have to know about it.
 */
@Component
class BuildAuditTrailAuthorizationContributor(
    private val securityService: SecurityService,
    private val trailService: TrailService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is Build

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> {
        val build = context as Build
        return listOf(
            Authorization(
                name = AUDIT_TRAIL,
                action = Authorization.VIEW,
                authorized = securityService.isProjectFunctionGranted(build, ProjectView::class.java) &&
                        trailService.isTrailAvailable(build),
            )
        )
    }

    companion object {
        /**
         * Name of the authorization of the trail of a build
         */
        const val AUDIT_TRAIL = "auditTrail"
    }
}
