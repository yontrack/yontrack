package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.repository.AccountGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.*

class AgentServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var agentService: AgentService

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var accountGroupRepository: AccountGroupRepository

    private fun slug() = uid("a-").lowercase()

    private fun person(): Account = asAdmin { doCreateAccount() }

    private fun registration(slug: String = slug(), owner: String? = null) = AgentRegistrationInput(
        slug = slug,
        displayName = "Agent $slug",
        tool = "Claude Code",
        description = "Writes the code",
        owner = owner,
    )

    private fun Account.register(slug: String = slug()): Account =
        asFixedAccount(this) { agentService.registerAgent(registration(slug)) }

    @Test
    fun `Registering an agent for oneself`() {
        val owner = person()
        val slug = slug()
        val agent = asFixedAccount(owner) {
            agentService.registerAgent(registration(slug))
        }
        assertEquals("$slug[agent]", agent.email)
        assertEquals("Agent $slug", agent.fullName)
        assertEquals(AccountKind.AGENT, agent.kind)
        assertEquals("Claude Code", agent.agentTool)
        assertEquals("Writes the code", agent.agentDescription)
        assertNotNull(agent.owner) {
            assertEquals(owner.id, it.id)
            assertEquals(owner.email, it.email)
            assertEquals(owner.fullName, it.fullName)
            assertNull(it.owner, "No recursion")
        }
        // No group, no ACL
        assertTrue(accountGroupRepository.findByAccount(agent.id()).isEmpty(), "No group")
        asAdmin {
            assertFalse(accountService.getGlobalRoleForAccount(agent).isPresent, "No global role")
        }
    }

    @Test
    fun `An administrator registers an agent for another owner`() {
        val owner = person()
        val agent = asAdmin {
            agentService.registerAgent(registration(owner = owner.email))
        }
        assertEquals(owner.id, agent.owner?.id)
    }

    @Test
    fun `A user cannot register an agent for somebody else`() {
        val owner = person()
        val other = person()
        asFixedAccount(other) {
            assertFailsWith<AccessDeniedException> {
                agentService.registerAgent(registration(owner = owner.email))
            }
        }
    }

    @Test
    fun `An agent is owned by a person`() {
        val agent = person().register()
        asAdmin {
            assertFailsWith<AgentOwnerNotHumanException> {
                agentService.registerAgent(registration(owner = agent.email))
            }
        }
    }

    @Test
    fun `Invalid slugs`() {
        val owner = person()
        listOf("", "Claude", "claude_code", "claude.code", "a".repeat(33), "with space").forEach { slug ->
            asFixedAccount(owner) {
                assertFailsWith<AgentSlugInvalidException>("Slug \"$slug\" is invalid") {
                    agentService.registerAgent(registration(slug))
                }
            }
        }
    }

    @Test
    fun `The longest slug fits`() {
        val slug = "a".repeat(22) + uid("").takeLast(10).lowercase()
        val agent = person().register(slug)
        assertEquals("$slug[agent]", agent.email)
    }

    @Test
    fun `A slug already taken`() {
        val slug = slug()
        person().register(slug)
        val other = person()
        asFixedAccount(other) {
            assertFailsWith<AgentSlugAlreadyTakenException> {
                agentService.registerAgent(registration(slug))
            }
        }
    }

    @Test
    fun `Updating an agent as its owner`() {
        val owner = person()
        val agent = owner.register()
        val updated = asFixedAccount(owner) {
            agentService.updateAgent(agent.id, AgentUpdateInput(displayName = "New name", tool = "Codex", description = null))
        }
        assertEquals("New name", updated.fullName)
        assertEquals("Codex", updated.agentTool)
        assertNull(updated.agentDescription)
        assertEquals(agent.email, updated.email, "The slug does not change")
    }

    @Test
    fun `Updating an agent is refused to somebody else`() {
        val agent = person().register()
        asFixedAccount(person()) {
            assertFailsWith<AccessDeniedException> {
                agentService.updateAgent(agent.id, AgentUpdateInput(displayName = "New name", tool = "Codex"))
            }
        }
    }

    @Test
    fun `Transferring an agent as an administrator`() {
        val agent = person().register()
        val newOwner = person()
        val transferred = asAdmin {
            agentService.transferAgent(agent.id, newOwner.email)
        }
        assertEquals(newOwner.id, transferred.owner?.id)
    }

    @Test
    fun `Transferring an agent is refused to its owner`() {
        val owner = person()
        val agent = owner.register()
        val newOwner = person()
        asFixedAccount(owner) {
            assertFailsWith<AccessDeniedException> {
                agentService.transferAgent(agent.id, newOwner.email)
            }
        }
    }

    @Test
    fun `Transferring an agent to another agent is refused`() {
        val agent = person().register()
        val other = person().register()
        asAdmin {
            assertFailsWith<AgentOwnerNotHumanException> {
                agentService.transferAgent(agent.id, other.email)
            }
        }
    }

    @Test
    fun `Deleting an agent as its owner deletes its tokens`() {
        val owner = person()
        val agent = owner.register()
        val token = asFixedAccount(owner) {
            agentService.generateAgentToken(agent.id, "ci")
        }
        asFixedAccount(owner) {
            agentService.deleteAgent(agent.id)
        }
        assertNull(accountRepository.findAccountByName(agent.email), "Agent deleted")
        assertNull(tokensService.findAccountByToken(token.value), "Token gone")
    }

    @Test
    fun `Deleting an agent is refused to somebody else`() {
        val agent = person().register()
        asFixedAccount(person()) {
            assertFailsWith<AccessDeniedException> {
                agentService.deleteAgent(agent.id)
            }
        }
    }

    @Test
    fun `Deleting the owner deletes its agents and their tokens`() {
        val owner = person()
        val agents = (1..2).map { owner.register() }
        val tokens = agents.map { agent ->
            asFixedAccount(owner) { agentService.generateAgentToken(agent.id, "ci") }
        }
        asAdmin {
            accountService.deleteAccount(owner.id)
        }
        agents.forEach { agent ->
            assertNull(accountRepository.findAccountByName(agent.email), "Agent ${agent.email} deleted")
        }
        tokens.forEach { token ->
            assertNull(tokensService.findAccountByToken(token.value), "Token gone")
        }
    }

    @Test
    fun `Token generation by the owner`() {
        val owner = person()
        val agent = owner.register()
        val token = asFixedAccount(owner) {
            agentService.generateAgentToken(agent.id, "ci")
        }
        assertTrue(token.value.isNotBlank(), "Token value returned")
        assertEquals("ci", token.name)
        // Several named tokens
        asFixedAccount(owner) {
            agentService.generateAgentToken(agent.id, "local")
        }
        assertEquals(
            setOf("ci", "local"),
            asAdmin { tokensService.getTokens(agent.id()) }.map { it.name }.toSet()
        )
        // Same name refused
        asFixedAccount(owner) {
            assertFailsWith<TokenGenerationNameAlreadyExistsException> {
                agentService.generateAgentToken(agent.id, "ci")
            }
        }
    }

    @Test
    fun `Token generation by an administrator`() {
        val agent = person().register()
        val token = asAdmin {
            agentService.generateAgentToken(agent.id, "ci")
        }
        assertEquals(agent.id, tokensService.findAccountByToken(token.value)?.account?.id)
    }

    @Test
    fun `Token generation refused to anybody else`() {
        val agent = person().register()
        asFixedAccount(person()) {
            assertFailsWith<AccessDeniedException> {
                agentService.generateAgentToken(agent.id, "ci")
            }
        }
    }

    @Test
    fun `Revoking the tokens of an agent`() {
        val owner = person()
        val agent = owner.register()
        val ci = asFixedAccount(owner) { agentService.generateAgentToken(agent.id, "ci") }
        val local = asFixedAccount(owner) { agentService.generateAgentToken(agent.id, "local") }
        asFixedAccount(owner) { agentService.revokeAgentToken(agent.id, "ci") }
        assertNull(tokensService.findAccountByToken(ci.value), "ci revoked")
        assertNotNull(tokensService.findAccountByToken(local.value), "local still there")
        asFixedAccount(owner) { agentService.revokeAllAgentTokens(agent.id) }
        assertNull(tokensService.findAccountByToken(local.value), "local revoked")
    }

    @Test
    fun `Authentication with an agent token resolves the agent account, with no rights`() {
        val project = asAdmin { project() }
        val owner = person()
        val agent = owner.register()
        val token = asFixedAccount(owner) { agentService.generateAgentToken(agent.id, "ci") }
        val oldContext = SecurityContextHolder.getContext()
        try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext())
            withGrantViewToAll {
                assertTrue(tokensService.useTokenForSecurityContext(token.value), "Token accepted")
                val account = assertNotNull(securityService.currentUser?.account)
                assertEquals(agent.id, account.id)
                assertEquals(AccountKind.AGENT, account.kind)
                assertEquals(owner.id, account.owner?.id)
                assertEquals("ci", securityService.currentActor?.tokenName)
                // No rights at all, not even those granted to every authenticated user
                assertFalse(
                    securityService.isProjectFunctionGranted(project.id(), ProjectView::class.java),
                    "An agent does not see the projects visible to all"
                )
                assertFalse(securityService.isGlobalFunctionGranted(ProjectCreation::class.java))
            }
        } finally {
            SecurityContextHolder.setContext(oldContext)
        }
    }

    @Test
    fun `An agent has no rights even when a role is assigned to it`() {
        val agent = person().register()
        asAdmin {
            accountService.saveGlobalPermission(
                PermissionTargetType.ACCOUNT,
                agent.id(),
                PermissionInput(Roles.GLOBAL_ADMINISTRATOR),
            )
        }
        val user = asAdmin { accountRepository.getAccount(agent.id) }
        asFixedAccount(user) {
            assertFalse(securityService.isGlobalFunctionGranted(AccountManagement::class.java))
            assertFalse(securityService.isGlobalFunctionGranted(ProjectCreation::class.java))
        }
    }

    @Test
    fun `An administrator sees all the agents, a user only their own`() {
        val owner = person()
        val other = person()
        val mine = owner.register()
        val theirs = other.register()
        asFixedAccount(owner) {
            val agents = agentService.getAgents().map { it.email }
            assertTrue(mine.email in agents)
            assertFalse(theirs.email in agents)
            assertTrue(agentService.getAgents(owner = other.email).isEmpty(), "Not the agents of another owner")
            assertNull(agentService.findAgent(theirs.id), "Not visible")
            assertNotNull(agentService.findAgent(mine.id), "Visible")
        }
        asAdmin {
            val agents = agentService.getAgents().map { it.email }
            assertTrue(mine.email in agents)
            assertTrue(theirs.email in agents)
            assertEquals(listOf(theirs.email), agentService.getAgents(owner = other.email).map { it.email })
            assertNotNull(agentService.findAgent(theirs.id), "Visible")
        }
    }

    @Test
    fun `An agent identifier cannot be created as an account`() {
        asAdmin {
            assertFailsWith<AgentIdentifierRefusedException> {
                accountService.create(AccountInput("Fake agent", "${slug()}[agent]", emptyList()))
            }
        }
    }

    @Test
    fun `An agent is not edited as an account`() {
        val agent = person().register()
        asAdmin {
            assertFailsWith<AgentAccountEditionException> {
                accountService.updateAccount(agent.id, AccountInput("New name", agent.email, emptyList()))
            }
        }
    }

    @Test
    fun `An agent is never offered as a permission target`() {
        val slug = slug()
        person().register(slug)
        val targets = asAdmin { accountService.searchPermissionTargets(slug) }
        assertTrue(targets.none { it.name == "$slug[agent]" }, "Agent not offered")
    }
}
