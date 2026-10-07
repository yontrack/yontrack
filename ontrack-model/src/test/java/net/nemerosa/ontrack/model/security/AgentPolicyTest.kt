package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.dashboards.DashboardEdition
import net.nemerosa.ontrack.model.labels.ProjectLabelManagement
import net.nemerosa.ontrack.model.structure.ID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentPolicyTest {

    private val owner = Account(
        id = ID.of(1),
        fullName = "Damien",
        email = "damien@example.com",
        role = SecurityRole.USER,
    )

    private val agent = Account.agent(
        slug = "claude-code-damien",
        displayName = "Claude Code",
        owner = owner,
        tool = "Claude Code",
        description = null,
    ).withId(ID.of(2))

    @Test
    fun `Reading and recording evidence are allowed`() {
        listOf(
            ProjectList::class.java,
        ).forEach { assertTrue(AgentPolicy.isGranted(it), it.simpleName) }
        listOf(
            ProjectView::class.java,
            BuildCreate::class.java,
            BuildConfig::class.java,
            ValidationRunCreate::class.java,
            ValidationRunStatusChange::class.java,
            ValidationRunStatusCommentEditOwn::class.java,
            PromotionRunCreate::class.java,
        ).forEach { assertTrue(AgentPolicy.isGranted(it), it.simpleName) }
    }

    @Test
    fun `Configuration and deletion are never granted`() {
        listOf(
            ProjectCreation::class.java,
            AccountManagement::class.java,
            AccountGroupManagement::class.java,
            ApplicationManagement::class.java,
            GlobalSettings::class.java,
            EventsAudit::class.java,
            DashboardEdition::class.java,
        ).forEach { assertFalse(AgentPolicy.isGranted(it), it.simpleName) }
        listOf(
            ProjectEdit::class.java,
            ProjectConfig::class.java,
            ProjectDelete::class.java,
            BranchCreate::class.java,
            BranchEdit::class.java,
            BranchDelete::class.java,
            PromotionLevelCreate::class.java,
            PromotionLevelEdit::class.java,
            PromotionLevelDelete::class.java,
            ValidationStampCreate::class.java,
            ValidationStampEdit::class.java,
            ValidationStampDelete::class.java,
            BuildEdit::class.java,
            BuildDelete::class.java,
            PromotionRunDelete::class.java,
            ValidationRunStatusCommentEdit::class.java,
            ProjectLabelManagement::class.java,
        ).forEach { assertFalse(AgentPolicy.isGranted(it), it.simpleName) }
    }

    @Test
    fun `An unknown function is denied by default`() {
        assertFalse(AgentPolicy.isGranted(UnknownGlobalFunction::class.java))
        assertFalse(AgentPolicy.isGranted(UnknownProjectFunction::class.java))
    }

    @Test
    fun `No function is both allowed and denied`() {
        assertTrue(AgentPolicy.allowedFunctions.intersect(AgentPolicy.deniedFunctions).isEmpty())
    }

    @Test
    fun `An agent gets the rights of its owner narrowed by the policy`() {
        val user = AccountAuthenticatedUser(
            account = agent,
            // What the owner holds: the administrator role, all functions
            authorisations = Authorisations().withGlobalRole(adminRole),
            groups = emptyList(),
            assignedGroups = emptyList(),
            mappedGroups = emptyList(),
            idpGroups = emptyList(),
        )
        assertTrue(user.isGranted(ProjectList::class.java))
        assertTrue(user.isGranted(1, BuildCreate::class.java))
        assertTrue(user.isGranted(1, ProjectView::class.java))
        assertFalse(user.isGranted(ProjectCreation::class.java), "Not in the policy")
        assertFalse(user.isGranted(1, ProjectEdit::class.java), "Not in the policy")
        assertFalse(user.isGranted(1, ValidationStampCreate::class.java), "Not in the policy")
    }

    @Test
    fun `An agent never gets a right its owner does not have`() {
        val user = AccountAuthenticatedUser(
            account = agent,
            // The owner sees the project 1, nothing else
            authorisations = Authorisations().withProjectRole(
                ProjectRoleAssociation(1, readOnlyRole)
            ),
            groups = emptyList(),
            assignedGroups = emptyList(),
            mappedGroups = emptyList(),
            idpGroups = emptyList(),
        )
        assertTrue(user.isGranted(1, ProjectView::class.java))
        assertFalse(user.isGranted(1, BuildCreate::class.java), "Owner cannot create builds")
        assertFalse(user.isGranted(2, ProjectView::class.java), "Owner cannot see project 2")
    }

    @Test
    fun `A person is not narrowed by the policy`() {
        val user = AccountAuthenticatedUser(
            account = owner,
            authorisations = Authorisations().withGlobalRole(adminRole),
            groups = emptyList(),
            assignedGroups = emptyList(),
            mappedGroups = emptyList(),
            idpGroups = emptyList(),
        )
        assertTrue(user.isGranted(ProjectCreation::class.java))
        assertTrue(user.isGranted(1, ProjectEdit::class.java))
    }

    @Test
    fun `Refusal message names the agent and the function`() {
        assertEquals(
            "agent claude-code-damien[agent] may not ProjectEdit (agent policy)",
            AgentPolicyException.function(agent, ProjectEdit::class.java).message,
        )
    }

    private interface UnknownGlobalFunction : GlobalFunction
    private interface UnknownProjectFunction : ProjectFunction

    private val adminRole = GlobalRole(
        id = Roles.GLOBAL_ADMINISTRATOR,
        name = "Administrator",
        description = "",
        globalFunctions = RolesService.defaultGlobalFunctions.toSet(),
        projectFunctions = RolesService.defaultProjectFunctions.toSet(),
    )

    private val readOnlyRole = ProjectRole(
        id = Roles.PROJECT_READ_ONLY,
        name = "Read only",
        description = "",
        functions = setOf(ProjectView::class.java),
    )
}
