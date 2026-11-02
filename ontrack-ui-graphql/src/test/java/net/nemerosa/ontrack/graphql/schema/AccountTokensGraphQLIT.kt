package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountManagement
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Who reads the tokens of an account: the account itself, the owner of an agent, and whoever holds
 * [AccountManagement]. Nobody else, even though an account can be reached through its groups.
 */
class AccountTokensGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private val notGranted = "Global function 'AccountManagement' is not granted."

    private val groupAccounts = "{ user { assignedGroups { accounts { email tokens { name } } } } }"

    /**
     * Two accounts in the same group, the second one having a token.
     */
    private fun colleagues(): Pair<Account, Account> = asAdmin {
        val group = doCreateAccountGroup()
        val other = doCreateAccount(group)
        tokensService.generateToken(other.id(), TokenOptions(name = uid("t-")))
        doCreateAccount(group) to other
    }

    @Test
    fun `A user does not read the tokens of another account`() {
        val (account, _) = colleagues()
        asFixedAccount(account) {
            runWithMatchingError(groupAccounts, errorMessage = notGranted)
        }
    }

    @Test
    fun `A user reads their own tokens, and those of their agents`() {
        val account = asAdmin { doCreateAccount() }
        val tokenName = uid("t-")
        asAdmin { tokensService.generateToken(account.id(), TokenOptions(name = tokenName)) }
        agentTestSupport.registerAgent(account)
        asFixedAccount(account) {
            run("{ user { account { tokens { name } agents { tokens { name } } } } }") { data ->
                val node = data.path("user").path("account")
                assertEquals(listOf(tokenName), node.path("tokens").toList().map { it.path("name").asString() })
                assertEquals(
                    listOf("ci"),
                    node.path("agents").path(0).path("tokens").toList().map { it.path("name").asString() },
                )
            }
        }
    }

    @Test
    fun `The holder of AccountManagement reads the tokens of any account`() {
        val (_, other) = colleagues()
        asUserWith<AccountManagement> {
            run("{ accounts(id: ${other.id()}) { tokens { name } } }") { data ->
                assertEquals(1, data.path("accounts").path(0).path("tokens").size())
            }
        }
    }

    @Test
    fun `An agent reads its own tokens, not those of its owner`() {
        val owner = asAdmin { doCreateAccount() }
        val agent = agentTestSupport.registerAgent(owner)
        agentTestSupport.withToken(agent.token) {
            run("{ user { account { tokens { name } } } }") { data ->
                assertEquals(
                    listOf("ci"),
                    data.path("user").path("account").path("tokens").toList().map { it.path("name").asString() },
                )
            }
            runWithMatchingError(
                "{ user { account { owner { tokens { name } } } } }",
                errorMessage = "agent ${agent.account.email} may not AccountManagement (agent policy)",
            )
        }
    }

}
