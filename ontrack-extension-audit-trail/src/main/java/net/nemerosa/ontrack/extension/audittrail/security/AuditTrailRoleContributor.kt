package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.model.security.ProjectFunction
import net.nemerosa.ontrack.model.security.RoleContributor
import net.nemerosa.ontrack.model.security.Roles
import org.springframework.stereotype.Component

/**
 * Grants [EvidenceDelete] to the project owner.
 *
 * The administrator gets it without being listed, as it gets every contributed function. No other
 * built-in role gets it: the automation and controller global roles create validation runs — they
 * are what CI runs as — and must not erase the evidence they attach.
 */
@Component
class AuditTrailRoleContributor : RoleContributor {

    override fun getProjectFunctionContributionsForProjectRoles(): Map<String, List<Class<out ProjectFunction>>> =
        mapOf(Roles.PROJECT_OWNER to listOf(EvidenceDelete::class.java))
}
