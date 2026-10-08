package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An agent reads its own policy (#2027): `user.account.kind/owner` and `user.agentPolicy`, for the
 * promotion levels.
 */
class AgentPolicyGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private lateinit var owner: Account

    private fun agentOf(ownerGlobalRole: String): AgentTestSupport.TestAgent {
        owner = asAdmin { doCreateAccountWithGlobalRole(ownerGlobalRole) }
        return agentTestSupport.registerAgent(owner = owner)
    }

    private fun PromotionLevel.admitAgents(admitted: Boolean = true) {
        asAdmin {
            setProperty(this, AgentsAdmittedPropertyType::class.java, AgentsAdmittedProperty(admitted = admitted))
        }
    }

    private val policyQuery = """
        query AgentPolicy(${'$'}project: String!, ${'$'}branch: String) {
            user {
                account {
                    kind
                    owner {
                        fullName
                        email
                    }
                }
                agentPolicy(project: ${'$'}project) {
                    owner
                    canRecordEvidence
                    promotionLevels(branch: ${'$'}branch) {
                        id
                        branch
                        name
                    }
                    slots {
                        id
                    }
                }
            }
        }
    """

    private fun policy(project: Project, branch: String? = null): JsonNode =
        run(policyQuery, mapOf("project" to project.name, "branch" to branch)).path("user")

    private fun JsonNode.levels(): List<Pair<String, String>> =
        path("agentPolicy").path("promotionLevels").toList().map {
            it.path("branch").asString() to it.path("name").asString()
        }

    @Test
    fun `The account of a person is a person without owner, and has no agent policy`() {
        asAdmin {
            project {
                val user = asUser().withView(this).call {
                    policy(this)
                }
                assertEquals("HUMAN", user.path("account").path("kind").asString())
                assertTrue(user.path("account").path("owner").isNull, "No owner")
                assertTrue(user.path("agentPolicy").isNull, "No agent policy for a person")
            }
        }
    }

    @Test
    fun `The account of an agent gives its kind and its owner`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        asAdmin {
            project {
                val user = agentTestSupport.withToken(agent.token) { policy(this) }
                assertEquals("AGENT", user.path("account").path("kind").asString())
                assertEquals(owner.fullName, user.path("account").path("owner").path("fullName").asString())
                assertEquals(owner.email, user.path("account").path("owner").path("email").asString())
            }
        }
    }

    @Test
    fun `The policy of an agent lists the levels which admit agents, on the enabled branches`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        asAdmin {
            project {
                branch("main") {
                    promotionLevel("SILVER")
                    promotionLevel("GOLD").admitAgents()
                    promotionLevel("PLATINUM").admitAgents(admitted = false)
                }
                branch("release-1.0") {
                    promotionLevel("GOLD").admitAgents()
                }
                val disabled = branch("old") {
                    promotionLevel("GOLD").admitAgents()
                }
                structureService.disableBranch(disabled)

                val user = agentTestSupport.withToken(agent.token) { policy(this) }
                val policy = user.path("agentPolicy")
                assertEquals(owner.fullName, policy.path("owner").asString())
                assertTrue(policy.path("canRecordEvidence").asBoolean(), "The owner can record evidence")
                assertEquals(
                    listOf("main" to "GOLD", "release-1.0" to "GOLD"),
                    user.levels().sortedBy { it.first },
                )
                assertTrue(policy.path("slots").isEmpty, "No slot")
            }
        }
    }

    @Test
    fun `The levels of the policy of an agent are restricted to a branch`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        asAdmin {
            project {
                lateinit var gold: PromotionLevel
                branch("main") {
                    gold = promotionLevel("GOLD")
                    gold.admitAgents()
                }
                branch("release-1.0") {
                    promotionLevel("GOLD").admitAgents()
                }
                val user = agentTestSupport.withToken(agent.token) { policy(this, branch = "main") }
                assertEquals(listOf("main" to "GOLD"), user.levels())
                assertEquals(
                    gold.id(),
                    user.path("agentPolicy").path("promotionLevels").first().path("id").asInt()
                )
            }
        }
    }

    @Test
    fun `The policy of an agent reflects the rights of its owner`() {
        val agent = agentOf(Roles.GLOBAL_READ_ONLY)
        asAdmin {
            project {
                branch("main") {
                    promotionLevel("GOLD").admitAgents()
                }
                val policy = agentTestSupport.withToken(agent.token) { policy(this) }.path("agentPolicy")
                assertEquals(owner.fullName, policy.path("owner").asString())
                assertFalse(policy.path("canRecordEvidence").asBoolean(), "The owner cannot record evidence")
                assertTrue(policy.path("promotionLevels").isEmpty, "The owner cannot promote: no level")
            }
        }
    }

    @Test
    fun `The policy of an agent on a project its owner cannot see is an error`() {
        owner = asAdmin { doCreateAccount() }
        val agent = agentTestSupport.registerAgent(owner = owner)
        val project = asAdmin { project() }
        withNoGrantViewToAll {
            agentTestSupport.withToken(agent.token) {
                runWithError(
                    policyQuery,
                    mapOf("project" to project.name),
                    errorMessage = "Project name not found: ${project.name}",
                )
            }
        }
    }
}
