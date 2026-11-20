package net.nemerosa.ontrack.model.security

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Global roles add up (ADR 0023): an account or a group holding several global roles is granted
 * the union of their functions.
 */
class AuthorisationsTest {

    private val accountManager = GlobalRole(
        id = "ACCOUNT_MANAGER",
        name = "Account manager",
        description = "",
        globalFunctions = setOf(AccountManagement::class.java),
        projectFunctions = setOf(ProjectView::class.java),
    )

    private val projectCreator = GlobalRole(
        id = "PROJECT_CREATOR",
        name = "Project creator",
        description = "",
        globalFunctions = setOf(ProjectCreation::class.java),
        projectFunctions = setOf(BuildCreate::class.java),
    )

    @Test
    fun `No global role grants nothing`() {
        val authorisations = Authorisations()
        assertFalse(authorisations.isGranted(AccountManagement::class.java))
        assertFalse(authorisations.isGranted(1, ProjectView::class.java))
    }

    @Test
    fun `Two global roles grant the global functions of both`() {
        val authorisations = Authorisations().withGlobalRoles(listOf(accountManager, projectCreator))
        assertTrue(authorisations.isGranted(AccountManagement::class.java))
        assertTrue(authorisations.isGranted(ProjectCreation::class.java))
        assertFalse(authorisations.isGranted(GlobalSettings::class.java))
    }

    @Test
    fun `Two global roles grant the project functions of both on every project`() {
        val authorisations = Authorisations().withGlobalRoles(listOf(accountManager, projectCreator))
        assertTrue(authorisations.isGranted(1, ProjectView::class.java))
        assertTrue(authorisations.isGranted(2, BuildCreate::class.java))
        assertFalse(authorisations.isGranted(1, ProjectDelete::class.java))
    }

    @Test
    fun `Adding a global role keeps the ones already there`() {
        val authorisations = Authorisations()
            .withGlobalRole(accountManager)
            .withGlobalRole(projectCreator)
        assertTrue(authorisations.isGranted(AccountManagement::class.java))
        assertTrue(authorisations.isGranted(ProjectCreation::class.java))
    }

    @Test
    fun `A missing global role adds nothing`() {
        val authorisations = Authorisations()
            .withGlobalRole(accountManager)
            .withGlobalRole(null)
        assertTrue(authorisations.isGranted(AccountManagement::class.java))
    }
}
