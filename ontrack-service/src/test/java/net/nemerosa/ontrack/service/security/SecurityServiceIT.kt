package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.NoAuthTest
import net.nemerosa.ontrack.model.security.*
import org.apache.commons.lang3.Strings
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.*

class SecurityServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var roleService: RolesService

    @Test
    @AsAdminTest
    fun `Running as admin`() {
        project {
            securityService.asAdmin {
                roleService.globalFunctions.forEach {
                    assertTrue(
                        securityService.isGlobalFunctionGranted(it),
                        "$it is granted for admin"
                    )
                }
                roleService.projectFunctions.forEach {
                    assertTrue(
                        securityService.isProjectFunctionGranted(this, it),
                        "$it is granted for admin"
                    )
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun currentAccount() {
        val account = asUser().call { securityService.currentUser?.account }
        assertNotNull(account)
    }

    @Test
    @AsAdminTest
    fun currentAccount_none() {
        val account = asAnonymous().call { securityService.currentUser?.account }
        assertNull(account)
    }

    @Test
    fun runner_function() {
        // Function that needs an authentication context
        val fn = { s: String -> "$s -> $contextName" }
        // Testing outside a context
        assertEquals("test -> none", fn("test"))
        // With a context
        val securedFn = asUser().with(ProjectCreation::class.java).call { securityService.runner(fn) }
        // Calls the secured function
        assertEquals("test -> TestingAuthenticationToken", securedFn("test"))
    }

    @Test
    @AsAdminTest
    fun read_only_on_one_project() {
        withNoGrantViewToAll {

            // Creates two projects
            val (id, name) = doCreateProject()
            val p2 = doCreateProject()
            // Creates an account authorised to access only one project
            val account = doCreateAccountWithProjectRole(p2, Roles.PROJECT_READ_ONLY)
            asFixedAccount(account).call {

                // With this account, gets the list of projects
                val list = structureService.projectList
                // Checks we only have one project
                assertEquals(1, list.size)
                assertEquals(p2.name, list[0].name)
                // Access to the authorised project
                assertTrue(structureService.findProjectByName(p2.name).isPresent)
                assertNotNull(structureService.getProject(p2.id))
                // No access to the other project
                assertFalse(structureService.findProjectByName(name).isPresent)
                try {
                    structureService.getProject(id)
                    fail("Project is not authorised")
                } catch (ignored: AccessDeniedException) {
                    assertTrue(true, "Project cannot be found")
                }
                true
            }
        }
    }

    @Test
    @AsAdminTest
    fun read_only_on_all_projects() {
        withNoGrantViewToAll {

            // Creates two projects
            val (id, name) = doCreateProject()
            val (id1, name1) = doCreateProject()
            // Creates an account authorised to access all projects
            val account = doCreateAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY)
            asFixedAccount(account).call {

                // With this account, gets the list of projects
                val list = structureService.projectList
                // Checks we only have the two projects (among all others)
                assertTrue(list.size >= 2)
                assertTrue(list.stream().anyMatch { (_, name2) -> Strings.CS.equals(name, name2) })
                assertTrue(list.stream().anyMatch { (_, name2) -> Strings.CS.equals(name1, name2) })
                // Access to the projects
                assertTrue(structureService.findProjectByName(name).isPresent)
                assertNotNull(structureService.getProject(id))
                assertTrue(structureService.findProjectByName(name1).isPresent)
                assertNotNull(structureService.getProject(id1))
                true
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Participant in all projects`() {
        withNoGrantViewToAll {

            // Creates two projects
            val (id, name) = doCreateProject()
            val (id1, name1) = doCreateProject()
            // Creates an account authorised to access all projects
            val account = doCreateAccountWithGlobalRole(Roles.GLOBAL_PARTICIPANT)
            asFixedAccount(account).call {
                // With this account, gets the list of projects
                val list = structureService.projectList
                // Checks we only have the two projects (among all others)
                assertTrue(list.size >= 2)
                assertTrue(list.stream().anyMatch { (_, name2) -> Strings.CS.equals(name, name2) })
                assertTrue(list.stream().anyMatch { (_, name2) -> Strings.CS.equals(name1, name2) })
                // Access to the projects
                assertTrue(structureService.findProjectByName(name).isPresent)
                assertNotNull(structureService.getProject(id))
                assertTrue(structureService.findProjectByName(name1).isPresent)
                assertNotNull(structureService.getProject(id1))
                // Checks the access right for participation
                securityService.checkProjectFunction(id.value, ValidationRunStatusChange::class.java)
                securityService.checkProjectFunction(id.value, ValidationRunStatusCommentEditOwn::class.java)
                //
                true
            }
        }
    }

    @Test
    @NoAuthTest
    fun `As admin has a name`() {
        securityService.asAdmin {
            // The run-as account has a name
            val name = securityService.currentUser?.name
            assertNotNull(name, "Run-as admin name is defined") {
                assertTrue(it.isNotBlank(), "Run-as admin name is not blank")
            }
        }
    }

    companion object {
        private val contextName: String
            get() {
                val authentication = SecurityContextHolder.getContext().authentication
                return if (authentication != null) authentication.javaClass.simpleName else "none"
            }
    }
}