package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

@Component
class ProjectAuthorizationContributor(
    private val securityService: SecurityService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is Project

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> =
        (context as Project).let { project ->
            listOf(
                Authorization(
                    CoreAuthorizationContributor.PROJECT,
                    Authorization.CONFIG,
                    securityService.isProjectFunctionGranted<ProjectConfig>(project)
                ),
                Authorization(
                    CoreAuthorizationContributor.PROJECT,
                    Authorization.DISABLE,
                    securityService.isProjectFunctionGranted<ProjectDisable>(project)
                ),
                Authorization(
                    CoreAuthorizationContributor.PROJECT,
                    Authorization.EDIT,
                    securityService.isProjectFunctionGranted<ProjectEdit>(project)
                ),
                Authorization(
                    CoreAuthorizationContributor.PROJECT,
                    Authorization.DELETE,
                    securityService.isProjectFunctionGranted<ProjectDelete>(project)
                ),
                // Managing the permissions of the project: the project permissions page (#2164)
                Authorization(
                    CoreAuthorizationContributor.PROJECT,
                    PERMISSIONS,
                    securityService.isProjectFunctionGranted<ProjectAuthorisationMgt>(project)
                ),
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    Authorization.CREATE,
                    securityService.isProjectFunctionGranted<BranchCreate>(project)
                )
            )
        }

    companion object {
        /**
         * Action of managing the permissions of a project: granting and revoking its project roles.
         */
        const val PERMISSIONS = "permissions"
    }
}
