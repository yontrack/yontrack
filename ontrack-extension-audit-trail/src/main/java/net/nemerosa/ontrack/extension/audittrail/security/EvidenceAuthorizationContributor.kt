package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component

/**
 * `evidence/delete` on a validation run, for the UI to know who may delete its evidences: the
 * [EvidenceDelete] function on its project, while the licence is on — a deletion is written to the
 * trail.
 */
@Component
class EvidenceAuthorizationContributor(
    private val securityService: SecurityService,
    private val auditTrailLicense: AuditTrailLicense,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is ValidationRun

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> = listOf(
        Authorization(
            name = EVIDENCE,
            action = Authorization.DELETE,
            authorized = auditTrailLicense.auditTrailEnabled &&
                    securityService.isProjectFunctionGranted(context as ValidationRun, EvidenceDelete::class.java),
        )
    )

    companion object {
        /**
         * Name of the authorizations of the evidences
         */
        const val EVIDENCE = "evidence"
    }
}
