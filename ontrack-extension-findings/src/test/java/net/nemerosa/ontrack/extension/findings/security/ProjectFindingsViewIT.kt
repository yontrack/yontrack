package net.nemerosa.ontrack.extension.findings.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AsAdminTest
class ProjectFindingsViewIT : AbstractDSLTestSupport() {

    @Test
    fun `Every built-in global role granted the project view is granted the findings, and only those`() {
        Roles.GLOBAL_ROLES.forEach { id ->
            val role = rolesService.getGlobalRole(id).orElseThrow()
            assertEquals(
                role.isProjectFunctionGranted(ProjectView::class.java),
                role.isProjectFunctionGranted(ProjectFindingsView::class.java),
                "Global role $id"
            )
        }
    }

    @Test
    fun `Every built-in project role granted the project view is granted the findings, and only those`() {
        Roles.PROJECT_ROLES.forEach { id ->
            val role = rolesService.getProjectRole(id).orElseThrow()
            assertEquals(
                role.isGranted(ProjectView::class.java),
                role.isGranted(ProjectFindingsView::class.java),
                "Project role $id"
            )
        }
    }

    @Test
    fun `Built-in roles having the project view`() {
        // Guards the test above against a vacuous pass
        listOf(
            Roles.GLOBAL_ADMINISTRATOR,
            Roles.GLOBAL_AUTOMATION,
            Roles.GLOBAL_CONTROLLER,
            // Through ProjectConfig, a sub-function of ProjectView
            Roles.GLOBAL_CREATOR,
            Roles.GLOBAL_PARTICIPANT,
            Roles.GLOBAL_READ_ONLY,
            Roles.GLOBAL_VALIDATION_MANAGER,
        ).forEach { id ->
            assertTrue(
                rolesService.getGlobalRole(id).orElseThrow().isProjectFunctionGranted(ProjectFindingsView::class.java),
                "Global role $id"
            )
        }
        Roles.PROJECT_ROLES.forEach { id ->
            assertTrue(
                rolesService.getProjectRole(id).orElseThrow().isGranted(ProjectFindingsView::class.java),
                "Project role $id"
            )
        }
    }

    @Test
    fun `A read-only global role sees the findings of any project`() {
        val project = doCreateProject()
        asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
            assertTrue(securityService.isProjectFunctionGranted(project, ProjectFindingsView::class.java))
        }
    }

    @Test
    fun `A read-only project role sees the findings of its project only`() {
        val project = doCreateProject()
        val other = doCreateProject()
        val account = doCreateAccountWithProjectRole(project, Roles.PROJECT_READ_ONLY)
        asFixedAccount(account) {
            assertTrue(securityService.isProjectFunctionGranted(project, ProjectFindingsView::class.java))
            assertFalse(securityService.isProjectFunctionGranted(other, ProjectFindingsView::class.java))
        }
    }

    @Test
    fun `A user without any role does not see the findings`() {
        val project = doCreateProject()
        asUser {
            assertFalse(securityService.isProjectFunctionGranted(project, ProjectFindingsView::class.java))
        }
    }
}
