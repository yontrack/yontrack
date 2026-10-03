package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.model.security.*
import org.springframework.stereotype.Component

/**
 * Global `auditTrailStatus` authorization, for the UI to know who may see the audit trail status
 * page: `auditTrailStatus/view` with the [global settings][GlobalSettings], licence on or off.
 */
@Component
class AuditTrailAuthorizationContributor(
    private val securityService: SecurityService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is GlobalAuthorizationContext

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> = listOf(
        Authorization(
            name = AUDIT_TRAIL_STATUS,
            action = Authorization.VIEW,
            authorized = securityService.isGlobalFunctionGranted(GlobalSettings::class.java),
        )
    )

    companion object {
        /**
         * Name of the authorization of the audit trail status
         */
        const val AUDIT_TRAIL_STATUS = "auditTrailStatus"
    }
}
