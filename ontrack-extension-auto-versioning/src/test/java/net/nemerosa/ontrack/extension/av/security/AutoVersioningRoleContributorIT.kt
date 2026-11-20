package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoVersioningRoleContributorIT : AbstractDSLTestSupport() {

    private fun projectRole(id: String) = rolesService.getProjectRole(id).orElseThrow()
    private fun globalRole(id: String) = rolesService.getGlobalRole(id).orElseThrow()

    @Test
    fun `The auto-versioning controller views the project and reschedules its orders, nothing else`() {
        val role = projectRole(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER)
        assertEquals("Auto-versioning controller", role.name)
        assertEquals(
            setOf(ProjectView::class.java, AutoVersioningReschedule::class.java),
            role.functions,
        )
    }

    @Test
    fun `The global auto-versioning controller does the same on every project, nothing else`() {
        val role = globalRole(AutoVersioningRoleContributor.GLOBAL_AUTO_VERSIONING_CONTROLLER)
        assertEquals("Global auto-versioning controller", role.name)
        assertTrue(role.globalFunctions.isEmpty(), "No global function")
        assertEquals(
            setOf(ProjectView::class.java, AutoVersioningReschedule::class.java),
            role.projectFunctions,
        )
    }

    @Test
    fun `The controller roles are listed with the other roles`() {
        assertTrue(rolesService.projectRoles.any { it.id == AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER })
        assertTrue(rolesService.globalRoles.any { it.id == AutoVersioningRoleContributor.GLOBAL_AUTO_VERSIONING_CONTROLLER })
    }

    @Test
    fun `Owners, project managers, the automation and the administrators reschedule the orders`() {
        listOf(Roles.PROJECT_OWNER, Roles.PROJECT_MANAGER).forEach {
            assertTrue(AutoVersioningReschedule::class.java in projectRole(it).functions, it)
        }
        listOf(Roles.GLOBAL_AUTOMATION, Roles.GLOBAL_ADMINISTRATOR).forEach {
            assertTrue(AutoVersioningReschedule::class.java in globalRole(it).projectFunctions, it)
        }
    }

    @Test
    fun `Participants and the other built-in roles do not reschedule the orders`() {
        listOf(
            Roles.PROJECT_PARTICIPANT,
            Roles.PROJECT_PROMOTER,
            Roles.PROJECT_VALIDATION_MANAGER,
            Roles.PROJECT_READ_ONLY,
        ).forEach {
            assertFalse(AutoVersioningReschedule::class.java in projectRole(it).functions, it)
        }
        listOf(
            Roles.GLOBAL_PARTICIPANT,
            Roles.GLOBAL_CREATOR,
            Roles.GLOBAL_CONTROLLER,
            Roles.GLOBAL_VALIDATION_MANAGER,
            Roles.GLOBAL_READ_ONLY,
        ).forEach {
            assertFalse(AutoVersioningReschedule::class.java in globalRole(it).projectFunctions, it)
        }
    }

}
