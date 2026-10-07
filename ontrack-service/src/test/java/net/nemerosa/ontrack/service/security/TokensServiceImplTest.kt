package net.nemerosa.ontrack.service.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Token
import net.nemerosa.ontrack.model.structure.TokenGenerator
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.TokensRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The actor set by an API token, and the agent session headers.
 */
class TokensServiceImplTest {

    private lateinit var tokensRepository: TokensRepository
    private lateinit var accountService: AccountService
    private lateinit var authenticationUserService: AuthenticationUserService
    private lateinit var service: TokensServiceImpl

    private val actor = slot<Actor>()

    private val owner = Account.user("Damien", "damien@yontrack.test").withId(ID.of(1))

    private val agent = Account.agent(
        slug = "claude",
        displayName = "Claude",
        owner = owner,
        tool = "Claude Code",
        description = null,
    ).withId(ID.of(2))

    private val headers = AgentSessionHeaders(id = "session-1", link = "https://claude.ai/code/session-1")

    @BeforeEach
    fun before() {
        tokensRepository = mockk(relaxed = true)
        accountService = mockk()
        authenticationUserService = mockk()
        val securityService = mockk<SecurityService>()
        every { securityService.asAdmin(any<() -> Account>()) } answers { firstArg<() -> Account>().invoke() }
        every { authenticationUserService.asUser(any(), capture(actor)) } returns Unit
        service = TokensServiceImpl(
            tokensRepository = tokensRepository,
            securityService = securityService,
            tokenGenerator = mockk<TokenGenerator>(),
            ontrackConfigProperties = OntrackConfigProperties(),
            accountService = accountService,
            authenticationUserService = authenticationUserService,
        )
    }

    private fun tokenOf(account: Account, value: String) {
        every { tokensRepository.findAccountByToken(value) } returns (account.id() to Token(
            name = "ci",
            value = value,
            creation = Time.now(),
            validUntil = null,
            lastUsed = null,
        ))
        every { accountService.getAccount(account.id) } returns account
    }

    @Test
    fun `An agent token sets the agent and its session`() {
        tokenOf(agent, "agent-token")
        assertTrue(service.useTokenForSecurityContext("agent-token", ActorVia.TOKEN, headers))
        assertEquals(
            Actor(
                account = "claude[agent]",
                via = ActorVia.TOKEN,
                tokenName = "ci",
                agent = ActorAgent(
                    name = "claude[agent]",
                    displayName = "Claude",
                    tool = "Claude Code",
                    owner = "damien@yontrack.test",
                ),
                agentSession = ActorAgentSession(id = "session-1", link = "https://claude.ai/code/session-1"),
            ),
            actor.captured,
        )
    }

    @Test
    fun `An agent token without session headers sets the agent only`() {
        tokenOf(agent, "agent-token")
        assertTrue(service.useTokenForSecurityContext("agent-token", ActorVia.TOKEN, null))
        assertEquals("claude[agent]", actor.captured.agent?.name)
        assertNull(actor.captured.agentSession)
    }

    @Test
    fun `A person's token ignores the session headers`() {
        tokenOf(owner, "person-token")
        assertTrue(service.useTokenForSecurityContext("person-token", ActorVia.TOKEN, headers))
        assertEquals(
            Actor(account = "damien@yontrack.test", via = ActorVia.TOKEN, tokenName = "ci"),
            actor.captured,
        )
    }
}
