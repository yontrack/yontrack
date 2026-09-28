package net.nemerosa.ontrack.extension.scorecard.security

import net.nemerosa.ontrack.model.security.GlobalFunction
import net.nemerosa.ontrack.model.security.RoleContributor
import net.nemerosa.ontrack.model.security.Roles
import org.springframework.stereotype.Component

/**
 * Grants [EstateManagement] to the built-in roles holding `LabelManagement`: the creator.
 *
 * The administrator gets it without being listed, as it gets every contributed function.
 */
@Component
class ScorecardRoleContributor : RoleContributor {

    override fun getGlobalFunctionContributionsForGlobalRoles(): Map<String, List<Class<out GlobalFunction>>> =
        mapOf(
            Roles.GLOBAL_CREATOR to listOf(EstateManagement::class.java),
        )
}
