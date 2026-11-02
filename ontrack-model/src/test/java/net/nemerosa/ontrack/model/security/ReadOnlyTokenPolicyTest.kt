package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.ID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadOnlyTokenPolicyTest {

    private val person = Account(
        id = ID.of(1),
        fullName = "Damien",
        email = "damien@example.com",
        role = SecurityRole.USER,
    )

    private val agent = Account.agent(
        slug = "claude-code-damien",
        displayName = "Claude Code",
        owner = person,
        tool = "Claude Code",
        description = null,
    ).withId(ID.of(2))

    private val adminRole = GlobalRole(
        id = Roles.GLOBAL_ADMINISTRATOR,
        name = "Administrator",
        description = "",
        globalFunctions = RolesService.defaultGlobalFunctions.toSet(),
        projectFunctions = RolesService.defaultProjectFunctions.toSet(),
    )

    private fun user(
        account: Account = person,
        authorisations: Authorisations = Authorisations().withGlobalRole(adminRole),
        readOnlyTokenName: String? = "ro",
    ) = AccountAuthenticatedUser(
        account = account,
        authorisations = authorisations,
        groups = emptyList(),
        assignedGroups = emptyList(),
        mappedGroups = emptyList(),
        idpGroups = emptyList(),
        readOnlyTokenName = readOnlyTokenName,
    )

    @Test
    fun `Functions are matched exactly`() {
        assertTrue(ProjectView::class.java.isAssignableFrom(BuildCreate::class.java), "BuildCreate extends ProjectView")
        assertTrue(ReadOnlyTokenPolicy.isGranted(ProjectView::class.java))
        assertFalse(ReadOnlyTokenPolicy.isGranted(BuildCreate::class.java))
    }

    @Test
    fun `A function nobody decided upon is denied`() {
        assertFalse(ReadOnlyTokenPolicy.isGranted(UndecidedFunction::class.java))
    }

    @Test
    fun `No function is both allowed and denied`() {
        assertTrue(ReadOnlyTokenPolicy.allowedFunctions.intersect(ReadOnlyTokenPolicy.deniedFunctions).isEmpty())
    }

    @Test
    fun `The functions gating both reads and writes are denied`() {
        listOf(
            AccountManagement::class.java,
            GlobalSettings::class.java,
            ApplicationManagement::class.java,
        ).forEach {
            assertFalse(ReadOnlyTokenPolicy.isGranted(it), it.simpleName)
            assertTrue(it.name in ReadOnlyTokenPolicy.deniedFunctions, it.simpleName)
        }
    }

    @Test
    fun `A read-only token gets the rights of its account narrowed by the policy`() {
        val user = user()
        assertTrue(user.isGranted(ProjectList::class.java))
        assertTrue(user.isGranted(EventsAudit::class.java))
        assertTrue(user.isGranted(1, ProjectView::class.java))
        assertFalse(user.isGranted(ProjectCreation::class.java))
        assertFalse(user.isGranted(AccountManagement::class.java))
        assertFalse(user.isGranted(1, BuildCreate::class.java))
        assertFalse(user.isGranted(1, ProjectEdit::class.java))
    }

    @Test
    fun `A read-only token never gets a right its account does not have`() {
        val user = user(authorisations = Authorisations.none())
        assertFalse(user.isGranted(ProjectList::class.java))
        assertFalse(user.isGranted(1, ProjectView::class.java))
    }

    @Test
    fun `The read-only token of an agent holds what both policies allow`() {
        val user = user(account = agent)
        assertTrue(user.isGranted(1, ProjectView::class.java))
        assertFalse(user.isGranted(1, BuildCreate::class.java), "Allowed by the agent policy, not by the token")
        assertFalse(user.isGranted(EventsAudit::class.java), "Allowed by the token, not by the agent policy")
    }

    @Test
    fun `No read-only token, no narrowing`() {
        val user = user(readOnlyTokenName = null)
        assertTrue(user.isGranted(ProjectCreation::class.java))
        assertTrue(user.isGranted(1, BuildCreate::class.java))
    }

    @Test
    fun `Refusal messages name the token and say why`() {
        assertEquals(
            "read-only token 'x' may not run a mutation",
            ReadOnlyTokenException.mutation("x").message,
        )
        assertEquals(
            "read-only token 'x' may not use BuildCreate",
            ReadOnlyTokenException.function("x", BuildCreate::class.java).message,
        )
        assertEquals(
            "read-only token 'x' may not call POST /rest/tokens/create/y",
            ReadOnlyTokenException.request("x", "POST", "/rest/tokens/create/y").message,
        )
    }

    private interface UndecidedFunction : GlobalFunction

}
