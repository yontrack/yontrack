package net.nemerosa.ontrack.extension.findings.security

import net.nemerosa.ontrack.model.security.AuthenticatedUser
import net.nemerosa.ontrack.model.security.Authorization
import net.nemerosa.ontrack.model.security.AuthorizationContributor
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

/**
 * `findings/view` on a project, from [ProjectFindingsView], so that the UI can hide what the
 * user would see empty.
 */
@Component
class FindingsAuthorizationContributor(
    private val securityService: SecurityService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is Project

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> =
        listOf(
            Authorization(
                name = FINDINGS,
                action = Authorization.VIEW,
                authorized = securityService.isProjectFunctionGranted(context as Project, ProjectFindingsView::class.java),
            )
        )

    companion object {
        const val FINDINGS = "findings"
    }
}
