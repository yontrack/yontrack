package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.model.security.*
import org.springframework.stereotype.Component

/**
 * The auto-versioning controller roles, and the auto-versioning functions of the built-in roles.
 *
 * The Administrator role gets [AutoVersioningReschedule] as every contributed function.
 */
@Component
class AutoVersioningRoleContributor : RoleContributor {

    companion object {
        /**
         * Project role allowing to reschedule the auto-versioning orders targeting the project.
         */
        const val AUTO_VERSIONING_CONTROLLER = "AUTO_VERSIONING_CONTROLLER"

        /**
         * Global role allowing to reschedule the auto-versioning orders targeting any project.
         */
        const val GLOBAL_AUTO_VERSIONING_CONTROLLER = "GLOBAL_AUTO_VERSIONING_CONTROLLER"

        /**
         * Functions of the controller roles, on their project or on all projects.
         */
        val controllerFunctions: List<Class<out ProjectFunction>> = listOf(
            ProjectView::class.java,
            AutoVersioningReschedule::class.java,
        )
    }

    override fun getGlobalRoles(): List<RoleDefinition> = listOf(
        RoleDefinition(
            id = GLOBAL_AUTO_VERSIONING_CONTROLLER,
            name = "Global auto-versioning controller",
            description = "Can view all projects and reschedule their auto-versioning orders",
        )
    )

    override fun getProjectRoles(): List<RoleDefinition> = listOf(
        RoleDefinition(
            id = AUTO_VERSIONING_CONTROLLER,
            name = "Auto-versioning controller",
            description = "Can view this project and reschedule the auto-versioning orders targeting it",
        )
    )

    override fun getProjectFunctionContributionsForGlobalRoles(): Map<String, List<Class<out ProjectFunction>>> =
        mapOf(
            Roles.GLOBAL_AUTOMATION to listOf(AutoVersioningReschedule::class.java),
            GLOBAL_AUTO_VERSIONING_CONTROLLER to controllerFunctions,
        )

    override fun getProjectFunctionContributionsForProjectRoles(): Map<String, List<Class<out ProjectFunction>>> =
        mapOf(
            Roles.PROJECT_OWNER to listOf(AutoVersioningReschedule::class.java),
            Roles.PROJECT_MANAGER to listOf(AutoVersioningReschedule::class.java),
            AUTO_VERSIONING_CONTROLLER to controllerFunctions,
        )
}
