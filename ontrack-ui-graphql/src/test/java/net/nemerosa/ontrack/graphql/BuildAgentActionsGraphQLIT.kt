package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Build.agentActions` - what the agents did on a build, read from its events (#2033).
 */
class BuildAgentActionsGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private val query = """
        query AgentActions(${'$'}id: Int!, ${'$'}offset: Int, ${'$'}size: Int) {
            build(id: ${'$'}id) {
                agentActions(offset: ${'$'}offset, size: ${'$'}size) {
                    pageInfo {
                        nextPage {
                            offset
                            size
                        }
                    }
                    pageItems {
                        id
                        eventType {
                            id
                        }
                        time
                        user
                        actor {
                            agent
                            sessionLink
                        }
                        message
                    }
                }
            }
        }
    """

    private fun agent(): AgentTestSupport.TestAgent =
        agentTestSupport.registerAgent(owner = asAdmin { doCreateAccount() })

    /**
     * Runs the [code] as the [agent], with an agent session, the system acting on its behalf: the
     * agent policy is not the point here.
     */
    private fun <T> asAgent(agent: AgentTestSupport.TestAgent, code: () -> T): T =
        agentTestSupport.withToken(
            agent.token,
            sessionId = "session-1",
            sessionLink = "https://claude.ai/code/session-1",
        ) {
            securityService.asAdmin(code)
        }

    private fun Build.validateAsAgent(agent: AgentTestSupport.TestAgent, vs: ValidationStamp) {
        asAgent(agent) {
            structureService.newValidationRun(
                this,
                ValidationRunRequest(
                    validationStampName = vs.name,
                    validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                )
            )
        }
    }

    private fun Build.promoteAsAgent(agent: AgentTestSupport.TestAgent, pl: PromotionLevel) {
        asAgent(agent) {
            structureService.newPromotionRun(
                PromotionRun.of(this, pl, securityService.currentSignature, null)
            )
        }
    }

    private fun agentActions(build: Build, offset: Int = 0, size: Int = 10): JsonNode =
        run(query, mapOf("id" to build.id(), "offset" to offset, "size" to size))
            .path("build").path("agentActions")

    private fun JsonNode.eventTypes(): List<String> =
        path("pageItems").values().map { it.path("eventType").path("id").asString() }

    @Test
    fun `The validation and the promotion of an agent and not the ones of a person`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        // A person validates and promotes
                        validate(vs)
                        promote(pl)
                        // The agent validates and promotes
                        validateAsAgent(agent, vs)
                        promoteAsAgent(agent, pl)

                        val actions = agentActions(this)
                        assertEquals(
                            listOf(EventFactory.NEW_PROMOTION_RUN.id, EventFactory.NEW_VALIDATION_RUN.id),
                            actions.eventTypes(),
                            "The agent's promotion and validation, newest first"
                        )
                        assertTrue(actions.path("pageInfo").path("nextPage").isNull, "No next page")
                        actions.path("pageItems").values().forEach { event ->
                            assertEquals(agent.account.email, event.path("actor").path("agent").asString())
                            assertEquals(
                                "https://claude.ai/code/session-1",
                                event.path("actor").path("sessionLink").asString()
                            )
                            assertTrue(
                                event.path("message").asString().contains(name),
                                "Message rendered with the build: ${event.path("message").asString()}"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `No agent action on a build of persons only`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs)
                        val actions = agentActions(this)
                        assertEquals(emptyList(), actions.eventTypes())
                        assertTrue(actions.path("pageInfo").path("nextPage").isNull, "No next page")
                    }
                }
            }
        }
    }

    @Test
    fun `Paging the agent actions`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val stamps = (1..3).map { validationStamp() }
                    build {
                        stamps.forEach { validateAsAgent(agent, it) }

                        val first = agentActions(this, offset = 0, size = 2)
                        assertEquals(2, first.path("pageItems").size())
                        val nextPage = first.path("pageInfo").path("nextPage")
                        assertEquals(2, nextPage.path("offset").asInt())
                        assertEquals(2, nextPage.path("size").asInt())

                        val second = agentActions(this, offset = 2, size = 2)
                        assertEquals(1, second.path("pageItems").size())
                        assertTrue(second.path("pageInfo").path("nextPage").isNull, "No next page")

                        // Newest first, and no event twice
                        val ids = (first.path("pageItems").values() + second.path("pageItems").values())
                            .map { it.path("id").asInt() }
                        assertEquals(ids.sortedDescending(), ids)
                        assertEquals(3, ids.toSet().size)
                    }
                }
            }
        }
    }

    @Test
    fun `Anyone who can see the build reads its agent actions`() {
        val agent = agent()
        val build = asAdmin {
            project<Build> {
                branch<Build> {
                    val vs = validationStamp()
                    build {
                        validateAsAgent(agent, vs)
                    }
                }
            }
        }
        build.asUserWithView {
            assertEquals(listOf(EventFactory.NEW_VALIDATION_RUN.id), agentActions(build).eventTypes())
        }
    }

    @Test
    fun `A link from a project the user cannot see is left out`() {
        val agent = agent()
        withNoGrantViewToAll {
            val (target, source) = asAdmin {
                val target = project<Build> { branch<Build> { build() } }
                val source = project<Build> { branch<Build> { build() } }
                // The agent links the hidden source build to the target build
                asAgent(agent) { source.linkTo(target) }
                target to source
            }
            // The admin sees the link
            asAdmin {
                assertEquals(listOf(EventFactory.NEW_BUILD_LINK.id), agentActions(target).eventTypes())
            }
            // A user who sees only the target does not
            target.asUserWithView {
                val actions = agentActions(target)
                assertEquals(emptyList(), actions.eventTypes(), "Link from ${source.project.name} left out")
            }
        }
    }
}
