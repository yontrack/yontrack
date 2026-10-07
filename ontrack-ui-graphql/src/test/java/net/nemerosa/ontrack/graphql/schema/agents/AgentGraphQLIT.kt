package net.nemerosa.ontrack.graphql.schema.agents

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var tokensService: TokensService

    private fun person(): Account = asAdmin { doCreateAccount() }

    private fun Account.registerAgent(slug: String = uid("a-").lowercase()): Int =
        asFixedAccount(this) {
            val data = run(
                """
                    mutation {
                        registerAgent(input: {
                            slug: "$slug",
                            displayName: "Claude Code for $email",
                            tool: "Claude Code",
                            description: "Writes the code",
                        }) {
                            errors { message }
                            agent { id }
                        }
                    }
                """
            )
            checkGraphQLUserErrors(data, "registerAgent").path("agent").path("id").asInt()
        }

    @Test
    fun `Registering an agent and listing it`() {
        val owner = person()
        val slug = uid("a-").lowercase()
        val id = owner.registerAgent(slug)
        asFixedAccount(owner) {
            run(
                """
                    {
                        agents {
                            id
                            email
                            fullName
                            kind
                            agentTool
                            agentDescription
                            owner { id email fullName kind }
                            tokens { name }
                        }
                        user {
                            account {
                                kind
                                agents { email }
                            }
                        }
                    }
                """
            ) { data ->
                val agents = data.path("agents")
                assertEquals(1, agents.size())
                val agent = agents.path(0)
                assertEquals(id, agent.path("id").asInt())
                assertEquals("$slug[agent]", agent.path("email").asString())
                assertEquals("Claude Code for ${owner.email}", agent.path("fullName").asString())
                assertEquals("AGENT", agent.path("kind").asString())
                assertEquals("Claude Code", agent.path("agentTool").asString())
                assertEquals("Writes the code", agent.path("agentDescription").asString())
                assertEquals(owner.email, agent.path("owner").path("email").asString())
                assertEquals("HUMAN", agent.path("owner").path("kind").asString())
                assertEquals(0, agent.path("tokens").size())
                val account = data.path("user").path("account")
                assertEquals("HUMAN", account.path("kind").asString())
                val owned = account.path("agents")
                assertEquals(listOf("$slug[agent]"), List(owned.size()) { owned.path(it).path("email").asString() })
            }
        }
    }

    @Test
    fun `Getting an agent by ID`() {
        val owner = person()
        val id = owner.registerAgent()
        asFixedAccount(owner) {
            run("""{ agents(id: $id) { id } }""") { data ->
                assertEquals(id, data.path("agents").path(0).path("id").asInt())
            }
        }
        // Not visible to anybody else
        asFixedAccount(person()) {
            run("""{ agents(id: $id) { id } }""") { data ->
                assertEquals(0, data.path("agents").size())
            }
        }
    }

    @Test
    fun `An invalid slug is a user error`() {
        val owner = person()
        asFixedAccount(owner) {
            val data = run(
                """
                    mutation {
                        registerAgent(input: {
                            slug: "Not Valid",
                            displayName: "Agent",
                            tool: "Codex",
                        }) {
                            errors { message }
                        }
                    }
                """
            )
            assertUserError(
                data,
                "registerAgent",
                "The agent slug \"Not Valid\" is not valid: it must have between 1 and 32 characters, " +
                        "each a lowercase letter, a digit or a dash."
            )
        }
    }

    @Test
    fun `Generating a token, using it and revoking it`() {
        val owner = person()
        val id = owner.registerAgent()
        val value = asFixedAccount(owner) {
            val data = run(
                """
                    mutation {
                        generateAgentToken(input: {id: $id, name: "ci"}) {
                            errors { message }
                            token { name value }
                        }
                    }
                """
            )
            checkGraphQLUserErrors(data, "generateAgentToken").path("token").path("value").asString()
        }
        assertTrue(value.isNotBlank(), "Token value returned")
        assertEquals(id, tokensService.findAccountByToken(value)?.account?.id())
        asFixedAccount(owner) {
            val data = run(
                """
                    mutation {
                        revokeAgentToken(input: {id: $id, name: "ci"}) {
                            errors { message }
                        }
                    }
                """
            )
            checkGraphQLUserErrors(data, "revokeAgentToken")
        }
        assertNull(tokensService.findAccountByToken(value), "Token revoked")
    }

    @Test
    fun `Updating, transferring and deleting an agent`() {
        val owner = person()
        val newOwner = person()
        val id = owner.registerAgent()
        asFixedAccount(owner) {
            val data = run(
                """
                    mutation {
                        updateAgent(input: {id: $id, displayName: "Renamed", tool: "Codex"}) {
                            errors { message }
                            agent { fullName agentTool agentDescription }
                        }
                    }
                """
            )
            val agent = checkGraphQLUserErrors(data, "updateAgent").path("agent")
            assertEquals("Renamed", agent.path("fullName").asString())
            assertEquals("Codex", agent.path("agentTool").asString())
            assertTrue(agent.path("agentDescription").isNull, "Description removed")
        }
        asAdmin {
            val data = run(
                """
                    mutation {
                        transferAgent(input: {id: $id, owner: "${newOwner.email}"}) {
                            errors { message }
                            agent { owner { email } }
                        }
                    }
                """
            )
            assertEquals(
                newOwner.email,
                checkGraphQLUserErrors(data, "transferAgent").path("agent").path("owner").path("email").asString()
            )
        }
        asFixedAccount(newOwner) {
            val data = run(
                """
                    mutation {
                        deleteAgent(input: {id: $id}) {
                            errors { message }
                        }
                    }
                """
            )
            checkGraphQLUserErrors(data, "deleteAgent")
            run("""{ agents { id } }""") { result ->
                assertEquals(0, result.path("agents").size())
            }
        }
    }
}
