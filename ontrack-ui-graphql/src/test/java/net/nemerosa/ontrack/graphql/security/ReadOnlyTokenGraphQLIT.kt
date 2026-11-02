package net.nemerosa.ontrack.graphql.security

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.structure.TokensService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Generating read-only tokens through GraphQL, and `user { tokenReadOnly }`.
 */
class ReadOnlyTokenGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Test
    fun `No token, no value for tokenReadOnly`() {
        asUser {
            run("{ user { tokenReadOnly } }") { data ->
                assertTrue(data.path("user").path("tokenReadOnly").isNull)
            }
        }
    }

    @Test
    fun `generateToken is full unless asked otherwise`() {
        asUser {
            run("""mutation { generateToken(input: {name: "full"}) { token { readOnly value } } }""") { data ->
                val token = data.path("generateToken").path("token")
                assertFalse(token.path("readOnly").asBoolean())
                val value = token.path("value").asString()
                agentTestSupport.withToken(value) {
                    run("{ user { tokenReadOnly } }") { user ->
                        assertFalse(user.path("user").path("tokenReadOnly").asBoolean())
                        assertFalse(user.path("user").path("tokenReadOnly").isNull)
                    }
                }
            }
        }
    }

    @Test
    fun `generateToken generates a read-only token on demand`() {
        asUser {
            run("""mutation { generateToken(input: {name: "ro", readOnly: true}) { token { readOnly value } } }""") { data ->
                val token = data.path("generateToken").path("token")
                assertTrue(token.path("readOnly").asBoolean())
                agentTestSupport.withToken(token.path("value").asString()) {
                    run("{ user { tokenReadOnly } }") { user ->
                        assertTrue(user.path("user").path("tokenReadOnly").asBoolean())
                    }
                }
            }
        }
    }

    @Test
    fun `generateAgentToken generates a read-only token on demand, and a full one by default`() {
        val owner = asAdmin { doCreateAccount() }
        val agent = agentTestSupport.registerAgent(owner)
        asFixedAccount(owner) {
            run(
                """mutation {
                    full: generateAgentToken(input: {id: ${agent.account.id()}, name: "full"}) { token { readOnly } }
                    ro: generateAgentToken(input: {id: ${agent.account.id()}, name: "ro", readOnly: true}) { token { readOnly } }
                }"""
            ) { data ->
                assertFalse(data.path("full").path("token").path("readOnly").asBoolean())
                assertTrue(data.path("ro").path("token").path("readOnly").asBoolean())
            }
        }
        assertEquals(
            mapOf("ci" to false, "full" to false, "ro" to true),
            asAdmin { tokensService.getTokens(agent.account).associate { it.name to it.readOnly } },
        )
    }

}
