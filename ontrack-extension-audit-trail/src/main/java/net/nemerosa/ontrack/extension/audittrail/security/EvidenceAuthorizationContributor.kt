package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component

/**
 * The authorizations of the evidences of a validation run, for the UI to know what to offer, the
 * licence being folded in:
 *
 * - `evidence/create` — who may upload an evidence: the creation of validation runs on its project,
 *   while the licence is on — an upload is written to the trail
 * - `evidence/delete` — who may delete its evidences: the [EvidenceDelete] function on its
 *   project, while the licence is on — a deletion is written to the trail
 */
@Component
class EvidenceAuthorizationContributor(
    private val securityService: SecurityService,
    private val auditTrailLicense: AuditTrailLicense,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is ValidationRun

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> {
        val validationRun = context as ValidationRun
        val licensed = auditTrailLicense.auditTrailEnabled
        return listOf(
            Authorization(
                name = EVIDENCE,
                action = Authorization.CREATE,
                authorized = licensed &&
                        securityService.isProjectFunctionGranted(validationRun, ValidationRunCreate::class.java),
            ),
            Authorization(
                name = EVIDENCE,
                action = Authorization.DELETE,
                authorized = licensed &&
                        securityService.isProjectFunctionGranted(validationRun, EvidenceDelete::class.java),
            ),
        )
    }

    companion object {
        /**
         * Name of the authorizations of the evidences
         */
        const val EVIDENCE = "evidence"
    }
}
