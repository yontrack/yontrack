package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Branch
import org.springframework.stereotype.Component

@Component
class BranchAuthorizationContributor(
    private val securityService: SecurityService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is Branch

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> =
        (context as Branch).let { branch ->
            listOf(
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    Authorization.CONFIG,
                    securityService.isProjectFunctionGranted<ProjectConfig>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    Authorization.DISABLE,
                    securityService.isProjectFunctionGranted<BranchDisable>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    Authorization.DELETE,
                    securityService.isProjectFunctionGranted<BranchDelete>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    "build_filter_manage",
                    securityService.isProjectFunctionGranted<BranchFilterMgt>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    "validation_stamp_filter_create",
                    securityService.isProjectFunctionGranted<ValidationStampFilterCreate>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.BRANCH,
                    "validation_stamp_filter_share",
                    securityService.isProjectFunctionGranted<ValidationStampFilterShare>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.PROMOTION_LEVEL,
                    Authorization.CREATE,
                    securityService.isProjectFunctionGranted<PromotionLevelCreate>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.VALIDATION_STAMP,
                    Authorization.CREATE,
                    securityService.isProjectFunctionGranted<ValidationStampCreate>(branch)
                ),
                Authorization(
                    CoreAuthorizationContributor.BUILD,
                    Authorization.CREATE,
                    securityService.isProjectFunctionGranted<BuildCreate>(branch)
                ),
            )
        }
}