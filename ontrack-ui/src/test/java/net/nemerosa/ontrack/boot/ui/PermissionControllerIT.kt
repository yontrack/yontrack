package net.nemerosa.ontrack.boot.ui

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.it.deprecatedUsages
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.model.security.PermissionInput
import net.nemerosa.ontrack.model.security.PermissionTargetType
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `/rest/accounts/permissions` endpoints are deprecated in V6, and removed in V7 (#2165): they
 * still answer, with their replace semantics, and each call is reported as a deprecated usage.
 */
@Suppress("DEPRECATION")
class PermissionControllerIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var controller: PermissionController

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private fun deprecated(item: String, code: () -> Unit) {
        val usages = meterRegistry.deprecatedUsages(DeprecationSurface.REST, item, code)
        assertEquals(1.0, usages, "One deprecated usage of $item")
    }

    @Test
    fun `Searching for permission targets still answers and is reported as deprecated`() {
        val group = doCreateAccountGroup()
        deprecated("GET /rest/accounts/permissions/search/{token}") {
            val targets = controller.searchPermissionTargets(group.name)
            assertEquals(
                listOf(PermissionTargetType.GROUP to group.id()),
                targets.map { it.type to it.id }
            )
        }
    }

    @Test
    fun `Listing the global permissions still answers and is reported as deprecated`() {
        val group = doCreateAccountGroupWithGlobalRole(Roles.GLOBAL_PARTICIPANT)
        deprecated("GET /rest/accounts/permissions/globals") {
            val roles = controller.globalPermissions
                .filter { it.target.type == PermissionTargetType.GROUP && it.target.id == group.id() }
                .map { it.role.id }
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), roles)
        }
    }

    @Test
    fun `Listing the global roles still answers and is reported as deprecated`() {
        deprecated("GET /rest/accounts/permissions/globals/roles") {
            val roles = controller.globalRoles.map { it.id }
            assertTrue(Roles.GLOBAL_ADMINISTRATOR in roles, "The administrator role is listed")
        }
    }

    @Test
    fun `Saving a global permission still replaces the roles and is reported as deprecated`() {
        val group = doCreateAccountGroupWithGlobalRole(Roles.GLOBAL_PARTICIPANT)
        accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
        deprecated("PUT /rest/accounts/permissions/globals/{type}/{id}") {
            controller.saveGlobalPermission(
                PermissionTargetType.GROUP,
                group.id(),
                PermissionInput(Roles.GLOBAL_READ_ONLY)
            )
        }
        assertEquals(
            listOf(Roles.GLOBAL_READ_ONLY),
            accountService.getGlobalRolesForAccountGroup(group).map { it.id }
        )
    }

    @Test
    fun `Deleting a global permission still removes all the roles and is reported as deprecated`() {
        val group = doCreateAccountGroupWithGlobalRole(Roles.GLOBAL_PARTICIPANT)
        accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
        deprecated("DELETE /rest/accounts/permissions/globals/{type}/{id}") {
            controller.deleteGlobalPermission(PermissionTargetType.GROUP, group.id())
        }
        assertEquals(emptyList(), accountService.getGlobalRolesForAccountGroup(group))
    }

    @Test
    fun `Listing the project roles still answers and is reported as deprecated`() {
        deprecated("GET /rest/accounts/permissions/projects/roles") {
            val roles = controller.projectRoles.map { it.id }
            assertTrue(Roles.PROJECT_OWNER in roles, "The owner role is listed")
        }
    }

    @Test
    fun `Listing the permissions of a project still answers and is reported as deprecated`() {
        val project = project()
        val group = doCreateAccountGroup()
        accountService.grantProjectPermission(
            project.id,
            PermissionTargetType.GROUP,
            group.id(),
            Roles.PROJECT_PARTICIPANT
        )
        deprecated("GET /rest/accounts/permissions/projects/{projectId}") {
            val roles = controller.getProjectPermissions(project.id)
                .filter { it.target.type == PermissionTargetType.GROUP && it.target.id == group.id() }
                .map { it.role.id }
            assertEquals(listOf(Roles.PROJECT_PARTICIPANT), roles)
        }
    }

    @Test
    fun `Saving a project permission still replaces the roles and is reported as deprecated`() {
        val project = project()
        val group = doCreateAccountGroup()
        accountService.grantProjectPermission(
            project.id,
            PermissionTargetType.GROUP,
            group.id(),
            Roles.PROJECT_PARTICIPANT
        )
        accountService.grantProjectPermission(
            project.id,
            PermissionTargetType.GROUP,
            group.id(),
            Roles.PROJECT_VALIDATION_MANAGER
        )
        deprecated("PUT /rest/accounts/permissions/projects/{projectId}/{type}/{id}") {
            controller.saveProjectPermission(
                project.id,
                PermissionTargetType.GROUP,
                group.id(),
                PermissionInput(Roles.PROJECT_READ_ONLY)
            )
        }
        assertEquals(
            listOf(Roles.PROJECT_READ_ONLY),
            accountService.getProjectPermissions(project.id)
                .filter { it.target.type == PermissionTargetType.GROUP && it.target.id == group.id() }
                .map { it.role.id }
        )
    }

    @Test
    fun `Deleting a project permission still removes all the roles and is reported as deprecated`() {
        val project = project()
        val group = doCreateAccountGroup()
        accountService.grantProjectPermission(
            project.id,
            PermissionTargetType.GROUP,
            group.id(),
            Roles.PROJECT_PARTICIPANT
        )
        accountService.grantProjectPermission(
            project.id,
            PermissionTargetType.GROUP,
            group.id(),
            Roles.PROJECT_VALIDATION_MANAGER
        )
        deprecated("DELETE /rest/accounts/permissions/projects/{projectId}/{type}/{id}") {
            controller.deleteProjectPermission(project.id, PermissionTargetType.GROUP, group.id())
        }
        assertTrue(
            accountService.getProjectPermissions(project.id)
                .none { it.target.type == PermissionTargetType.GROUP && it.target.id == group.id() },
            "The group has no role left on the project"
        )
    }
}
