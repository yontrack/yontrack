package net.nemerosa.ontrack.extension.findings.security

import net.nemerosa.ontrack.model.security.ProjectFunction
import net.nemerosa.ontrack.model.security.RoleContributor
import net.nemerosa.ontrack.model.security.Roles
import org.springframework.stereotype.Component

/**
 * Grants [ProjectFindingsView] to every built-in role which has the project view.
 *
 * The administrator gets it without being listed, as it gets every contributed function.
 */
@Component
class FindingsRoleContributor : RoleContributor {

    private val findingsView: List<Class<out ProjectFunction>> = listOf(ProjectFindingsView::class.java)

    override fun getProjectFunctionContributionsForGlobalRoles(): Map<String, List<Class<out ProjectFunction>>> =
        listOf(
            Roles.GLOBAL_CREATOR,
            Roles.GLOBAL_AUTOMATION,
            Roles.GLOBAL_CONTROLLER,
            Roles.GLOBAL_VALIDATION_MANAGER,
            Roles.GLOBAL_PARTICIPANT,
            Roles.GLOBAL_READ_ONLY,
        ).associateWith { findingsView }

    override fun getProjectFunctionContributionsForProjectRoles(): Map<String, List<Class<out ProjectFunction>>> =
        Roles.PROJECT_ROLES.associateWith { findingsView }
}
