package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.*

/**
 * The effective rights of a read-only token: those of its account, narrowed by the read-only token
 * policy, and by the agent policy for an agent.
 */
@AsAdminTest
class ReadOnlyTokenIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var agentService: AgentService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun readOnlyToken(account: Account): String = asAdmin {
        tokensService.generateToken(account.id(), TokenOptions(name = "ro", readOnly = true)).value
    }

    private fun <T> withToken(token: String, code: () -> T): T = agentTestSupport.withToken(token, code = code)

    @Test
    fun `A token is full by default, for a person and for an agent`() {
        val owner = asAdmin { doCreateAccount() }
        val agent = agentTestSupport.registerAgent(owner)
        val token = asAdmin { tokensService.generateToken(owner.id(), TokenOptions(name = "full")) }
        assertFalse(token.readOnly)
        asAdmin {
            assertFalse(tokensService.getTokens(owner).single().readOnly)
            assertFalse(tokensService.getTokens(agent.account).single().readOnly)
        }
    }

    @Test
    fun `An existing token stays full after the migration`() {
        val owner = asAdmin { doCreateAccount() }
        // As the rows written before the column existed
        namedParameterJdbcTemplate.update(
            "INSERT INTO TOKENS (ACCOUNT, NAME, VALUE, CREATION) VALUES (:account, 'old', :value, '2026-01-01T00:00:00.000')",
            mapOf("account" to owner.id(), "value" to uid("old-")),
        )
        asAdmin {
            assertFalse(tokensService.getTokens(owner).single().readOnly)
        }
    }

    @Test
    fun `A read-only token is generated and listed for a person`() {
        val token = asUser {
            tokensService.generateNewToken(TokenOptions(name = "ro", readOnly = true))
        }
        assertTrue(token.readOnly)
        assertNotNull(withToken(token.value) { securityService.currentUser }) {
            assertEquals("ro", (it as AccountAuthenticatedUser).readOnlyTokenName)
            assertTrue(asAdmin { tokensService.getTokens(it.account).single().readOnly }, "Listed as read-only")
        }
    }

    @Test
    fun `A read-only token is generated and listed for an agent`() {
        val owner = asAdmin { doCreateAccount() }
        val agent = agentTestSupport.registerAgent(owner)
        val token = asFixedAccount(owner) {
            agentService.generateAgentToken(agent.account.id, "ro", readOnly = true)
        }
        assertTrue(token.readOnly)
        asAdmin {
            val tokens = tokensService.getTokens(agent.account).associateBy { it.name }
            assertFalse(tokens.getValue("ci").readOnly)
            assertTrue(tokens.getValue("ro").readOnly)
        }
        withToken(token.value) {
            assertEquals(agent.account.email, securityService.currentUser?.account?.email)
        }
    }

    @Test
    fun `A full token is not narrowed`() {
        project {
            val owner = asAdmin { doCreateAccountWithProjectRole(this, Roles.PROJECT_OWNER) }
            val token = asAdmin { tokensService.generateToken(owner.id(), TokenOptions(name = "full")).value }
            withToken(token) {
                assertNull((securityService.currentUser as AccountAuthenticatedUser).readOnlyTokenName)
                assertTrue(securityService.isProjectFunctionGranted(id(), ProjectEdit::class.java))
            }
        }
    }

    @Test
    fun `A read-only token reads what its owner can, and never more`() {
        withNoGrantViewToAll<Unit> {
            val other = project()
            project {
                val owner = asAdmin { doCreateAccountWithProjectRole(this, Roles.PROJECT_OWNER) }
                val token = readOnlyToken(owner)
                withToken(token) {
                    assertEquals(name, structureService.getProject(id).name)
                    assertTrue(securityService.isProjectFunctionGranted(id(), ProjectView::class.java))
                    // Not more than the owner
                    assertFalse(securityService.isProjectFunctionGranted(other.id(), ProjectView::class.java))
                    // No write, even though ProjectEdit and BuildCreate imply ProjectView
                    assertFalse(securityService.isProjectFunctionGranted(id(), ProjectEdit::class.java))
                    assertFalse(securityService.isProjectFunctionGranted(id(), BuildCreate::class.java))
                }
            }
        }
    }

    @Test
    fun `A function the policy denies is refused with a message saying so`() {
        project {
            val owner = asAdmin { doCreateAccountWithProjectRole(this, Roles.PROJECT_OWNER) }
            withToken(readOnlyToken(owner)) {
                val ex = assertFailsWith<ReadOnlyTokenException> {
                    structureService.saveProject(withDescription("Changed"))
                }
                assertEquals("read-only token 'ro' may not use ProjectEdit", ex.message)
                assertFailsWith<ReadOnlyTokenException> {
                    securityService.checkGlobalFunction(AccountManagement::class.java)
                }
            }
            assertNotEquals("Changed", asAdmin { structureService.getProject(id).description })
        }
    }

    @Test
    fun `An administrator's read-only token holds what the policy allows only`() {
        val admin = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }
        withToken(readOnlyToken(admin)) {
            assertTrue(securityService.isGlobalFunctionGranted(ProjectList::class.java))
            assertTrue(securityService.isGlobalFunctionGranted(EventsAudit::class.java))
            assertFalse(securityService.isGlobalFunctionGranted(AccountManagement::class.java))
            assertFalse(securityService.isGlobalFunctionGranted(GlobalSettings::class.java))
            assertFalse(securityService.isGlobalFunctionGranted(ProjectCreation::class.java))
        }
    }

    @Test
    fun `The read-only token of an agent holds neither a right its owner lacks nor one the agent policy denies`() {
        withNoGrantViewToAll<Unit> {
            val other = project()
            project {
                val owner = asAdmin { doCreateAccountWithProjectRole(this, Roles.PROJECT_OWNER) }
                val agent = agentTestSupport.registerAgent(owner)
                val token = asFixedAccount(owner) {
                    agentService.generateAgentToken(agent.account.id, "ro", readOnly = true).value
                }
                withToken(token) {
                    assertTrue(securityService.isProjectFunctionGranted(id(), ProjectView::class.java))
                    assertFalse(securityService.isProjectFunctionGranted(other.id(), ProjectView::class.java), "Owner")
                    assertFalse(securityService.isProjectFunctionGranted(id(), BuildCreate::class.java), "Read-only")
                }
            }
            // Allowed to a read-only token, but not to an agent
            val admin = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }
            val agent = agentTestSupport.registerAgent(admin)
            val token = asFixedAccount(admin) {
                agentService.generateAgentToken(agent.account.id, "ro", readOnly = true).value
            }
            withToken(token) {
                assertFalse(securityService.isGlobalFunctionGranted(EventsAudit::class.java), "Agent policy")
            }
        }
    }

    @Test
    fun `A read-only token never generates a token`() {
        val account = asAdmin { doCreateAccount() }
        withToken(readOnlyToken(account)) {
            assertFailsWith<ReadOnlyTokenException> {
                tokensService.generateNewToken(TokenOptions(name = "full"))
            }
        }
        assertEquals(listOf("ro"), asAdmin { tokensService.getTokens(account).map { it.name } })
    }

    @Test
    fun `The policy does not apply inside asAdmin, on which the reads rely`() {
        project {
            val owner = asAdmin { doCreateAccountWithProjectRole(this, Roles.PROJECT_OWNER) }
            withToken(readOnlyToken(owner)) {
                assertTrue(
                    securityService.asAdmin { securityService.isGlobalFunctionGranted(AccountManagement::class.java) }
                )
                assertEquals(name, structureService.getProject(ID.of(id())).name)
            }
        }
    }

    @Test
    fun `A read-only token is refused through a hook`() {
        val account = asAdmin { doCreateAccount() }
        val token = readOnlyToken(account)
        val old = SecurityContextHolder.getContext()
        try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext())
            assertFalse(tokensService.useTokenForSecurityContext(token, ActorVia.WEBHOOK))
            assertNull(securityService.currentUser, "Nothing is authenticated")
            assertTrue(tokensService.useTokenForSecurityContext(token, ActorVia.TOKEN))
        } finally {
            SecurityContextHolder.setContext(old)
        }
    }

}
