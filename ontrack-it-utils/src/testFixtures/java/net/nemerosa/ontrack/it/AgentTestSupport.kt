package net.nemerosa.ontrack.it

import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.AgentRegistrationInput
import net.nemerosa.ontrack.model.security.AgentService
import net.nemerosa.ontrack.model.security.AgentSessionHeaders
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Registered agents for the integration tests, and calls authenticated with their tokens.
 */
@Component
class AgentTestSupport(
    private val securityTestSupport: SecurityTestSupport,
    private val agentService: AgentService,
    private val tokensService: TokensService,
) {

    /**
     * A registered agent and the value of one of its tokens.
     */
    data class TestAgent(
        val account: Account,
        val token: String,
    )

    /**
     * Registers an agent for the [owner], with a token named `ci`, as the owner does.
     */
    fun registerAgent(
        owner: Account,
        slug: String = uid("a-").lowercase(),
        displayName: String = "Agent $slug",
        tool: String = "Claude Code",
    ): TestAgent {
        val oldContext = securityTestSupport.setupSecurityContext(
            user = securityTestSupport.createOntrackAuthenticatedUser(owner),
        )
        return try {
            val account = agentService.registerAgent(
                AgentRegistrationInput(
                    slug = slug,
                    displayName = displayName,
                    tool = tool,
                )
            )
            TestAgent(
                account = account,
                token = agentService.generateAgentToken(account.id, "ci").value,
            )
        } finally {
            SecurityContextHolder.setContext(oldContext)
        }
    }

    /**
     * Runs the [code] authenticated by the [token], as the token filter does, with the agent session
     * headers [sessionId] and [sessionLink].
     */
    fun <T> withToken(
        token: String,
        sessionId: String? = null,
        sessionLink: String? = null,
        code: () -> T,
    ): T {
        val oldContext = SecurityContextHolder.getContext()
        return try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext())
            check(
                tokensService.useTokenForSecurityContext(
                    token = token,
                    via = ActorVia.TOKEN,
                    agentSession = AgentSessionHeaders(id = sessionId, link = sessionLink),
                )
            ) { "Token refused" }
            code()
        } finally {
            SecurityContextHolder.setContext(oldContext)
        }
    }
}
