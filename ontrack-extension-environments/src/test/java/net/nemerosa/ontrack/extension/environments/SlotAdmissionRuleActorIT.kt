package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.ActorAgentSession
import net.nemerosa.ontrack.model.structure.SignatureActor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The actor of the data and of the override of an admission rule for a pipeline (#2032): the agent
 * behind them, so that the deployment page can tell an agent from a person.
 *
 * Also checks their `actor` in GraphQL, beside the one of the changes of the pipeline.
 *
 * As in [SlotPipelineChangeActorIT], the agent policy is not the point here: the actions of the
 * agent are run by the system on its behalf, which keeps the agent as their actor.
 */
class SlotAdmissionRuleActorIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun withPipelineAndRule(code: (pipeline: SlotPipeline, config: SlotAdmissionRuleConfig) -> Unit) {
        asAdmin {
            slotTestSupport.withSlot { slot ->
                slotService.saveSlot(slot.withAgentsAdmitted(true))
                val config = SlotAdmissionRuleTestFixtures.testBranchPatternAdmissionRuleConfig(slot, includes = listOf(".*"))
                slotService.addAdmissionRuleConfig(config)
                slot.project.branch {
                    build {
                        val pipeline = slotService.startPipeline(slotService.getSlotById(slot.id), this)
                        code(pipeline, config)
                    }
                }
            }
        }
    }

    private fun status(pipeline: SlotPipeline, config: SlotAdmissionRuleConfig) =
        assertNotNull(
            asAdmin { slotService.findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(pipeline, config.id) },
            "Status of the rule"
        )

    @Test
    fun `The data and the override of a rule set by an agent carry the agent and its session`() {
        val agent = agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )
        withPipelineAndRule { pipeline, config ->
            agentTestSupport.withToken(agent.token, SESSION_ID, SESSION_LINK) {
                securityService.asAdmin {
                    slotService.setupAdmissionRule(pipeline, config, mapOf("any" to "value").asJson())
                    slotService.overrideAdmissionRule(pipeline, config, "Not needed")
                }
            }
            val expected = SignatureActor(
                agent = agent.account.email,
                displayName = "Claude",
                tool = "Claude Code",
                owner = agent.account.owner!!.email,
                session = ActorAgentSession(id = SESSION_ID, link = SESSION_LINK),
            )
            val status = status(pipeline, config)
            assertEquals(agent.account.email, status.data?.user)
            assertEquals(expected, status.data?.actor)
            assertEquals(agent.account.email, status.override?.user)
            assertEquals(expected, status.override?.actor)
        }
    }

    @Test
    fun `The data and the override of a rule set by a person have no actor`() {
        withPipelineAndRule { pipeline, config ->
            asAdmin {
                slotService.setupAdmissionRule(pipeline, config, mapOf("any" to "value").asJson())
                slotService.overrideAdmissionRule(pipeline, config, "Not needed")
            }
            val status = status(pipeline, config)
            assertNotNull(status.data) { assertNull(it.actor) }
            assertNotNull(status.override) { assertNull(it.actor) }
        }
    }

    @Test
    fun `Overriding a rule keeps the actor of its data`() {
        val agent = agentTestSupport.registerAgent(owner = asAdmin { doCreateAccount() })
        withPipelineAndRule { pipeline, config ->
            agentTestSupport.withToken(agent.token) {
                securityService.asAdmin {
                    slotService.setupAdmissionRule(pipeline, config, mapOf("any" to "value").asJson())
                }
            }
            asAdmin {
                slotService.overrideAdmissionRule(pipeline, config, "Not needed")
            }
            val status = status(pipeline, config)
            assertEquals(agent.account.email, status.data?.actor?.agent)
            assertNull(status.override?.actor)
        }
    }

    @Test
    fun `The actors of the changes, the data and the override of a pipeline in GraphQL`() {
        val agent = agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )
        withPipelineAndRule { pipeline, config ->
            agentTestSupport.withToken(agent.token, SESSION_ID, SESSION_LINK) {
                securityService.asAdmin {
                    slotService.setupAdmissionRule(pipeline, config, mapOf("any" to "value").asJson())
                    slotService.overrideAdmissionRule(pipeline, config, "Not needed")
                }
            }
            val data = asAdmin {
                run(
                    """
                        query Pipeline(${'$'}id: String!) {
                            slotPipelineById(id: ${'$'}id) {
                                changes {
                                    type
                                    user
                                    actor { kind agent displayName tool owner sessionId sessionLink }
                                }
                                admissionRules {
                                    data { user actor { agent sessionLink } }
                                    override { user actor { agent sessionLink } }
                                }
                            }
                        }
                    """,
                    mapOf("id" to pipeline.id)
                )
            }.path("slotPipelineById")
            val changes = data.path("changes").associateBy { it.path("type").asText() }
            // Started by the administrator: a person, no actor
            assertTrue(changes.getValue("STATUS").path("actor").isNull)
            changes.getValue("RULE_DATA").path("actor").let { actor ->
                assertEquals("agent", actor.path("kind").asText())
                assertEquals(agent.account.email, actor.path("agent").asText())
                assertEquals("Claude", actor.path("displayName").asText())
                assertEquals("Claude Code", actor.path("tool").asText())
                assertEquals(agent.account.owner!!.email, actor.path("owner").asText())
                assertEquals(SESSION_ID, actor.path("sessionId").asText())
                assertEquals(SESSION_LINK, actor.path("sessionLink").asText())
            }
            assertEquals(agent.account.email, changes.getValue("RULE_OVERRIDDEN").path("actor").path("agent").asText())
            val rule = data.path("admissionRules").first()
            assertEquals(agent.account.email, rule.path("data").path("actor").path("agent").asText())
            assertEquals(SESSION_LINK, rule.path("data").path("actor").path("sessionLink").asText())
            assertEquals(agent.account.email, rule.path("override").path("actor").path("agent").asText())
        }
    }

    companion object {
        private const val SESSION_ID = "session-2032"
        private const val SESSION_LINK = "https://claude.ai/code/session-2032"
    }
}
