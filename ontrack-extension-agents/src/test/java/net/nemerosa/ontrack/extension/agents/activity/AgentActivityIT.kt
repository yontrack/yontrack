package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.license.control.LicenseFeatureException
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The ACL-filtered read of the agent actions, and the activity of one agent (#2034).
 */
class AgentActivityIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Autowired
    private lateinit var agentActionsService: AgentActionsService

    private fun <T> withoutAgentsLicence(code: () -> T): T {
        devLicenseService.setFeatureEnabled(FEATURE_AGENTS, false)
        return try {
            code()
        } finally {
            devLicenseService.setFeatureEnabled(FEATURE_AGENTS, true)
        }
    }

    /**
     * An owner, a plain person with no role, and their agent.
     */
    private fun ownerAndAgent(): Pair<Account, AgentTestSupport.TestAgent> {
        val owner = asAdmin { doCreateAccount() }
        return owner to agentTestSupport.registerAgent(owner = owner)
    }

    /**
     * Runs the [code] as the [agent], in a session, the system acting on its behalf: the agent policy
     * is not the point here.
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

    /**
     * A project with one build, validated by the agent.
     */
    private fun projectValidatedBy(agent: AgentTestSupport.TestAgent): Build = asAdmin {
        project<Build> {
            branch<Build> {
                val vs = validationStamp()
                build {
                    validateAsAgent(agent, vs)
                }
            }
        }
    }

    private fun actions(
        agent: AgentTestSupport.TestAgent?,
        from: LocalDateTime? = null,
        to: LocalDateTime? = null,
        eventTypes: List<String>? = null,
        project: String? = null,
        offset: Int = 0,
        size: Int = 20,
    ): List<Event> =
        agentActionsService.findAgentActions(
            AgentActionsFilter(
                agent = agent?.account?.email,
                from = from,
                to = to,
                eventTypes = eventTypes,
                project = project,
            ),
            offset,
            size,
        ).pageItems

    private fun List<Event>.projects(): List<String> = map { it.getEntity<Project>(ProjectEntityType.PROJECT).name }

    // ---------------------------------------------------------------------------------------------
    // The read of the agent actions
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Only the events of agents, of the given agent`() {
        val (_, agent) = ownerAndAgent()
        val (_, other) = ownerAndAgent()
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        // A person validates
                        validate(vs)
                        // The two agents validate
                        validateAsAgent(agent, vs)
                        validateAsAgent(other, vs)

                        val actions = actions(agent)
                        assertEquals(1, actions.size)
                        val event = actions.first()
                        assertEquals(EventFactory.NEW_VALIDATION_RUN.id, event.eventType.id)
                        assertEquals(agent.account.email, event.signature?.actor?.agent)
                        assertEquals("https://claude.ai/code/session-1", event.signature?.actor?.session?.link)

                        // All the agents, on this project
                        val all = actions(null, project = project.name)
                        assertEquals(
                            setOf(agent.account.email, other.account.email),
                            all.mapNotNull { it.signature?.actor?.agent }.toSet(),
                            "Both agents and not the person"
                        )
                        assertEquals(2, all.size)
                    }
                }
            }
        }
    }

    @Test
    fun `Newest first, paged, at most 100 events a page`() {
        val (_, agent) = ownerAndAgent()
        asAdmin {
            project {
                branch {
                    val stamps = (1..3).map { validationStamp() }
                    build {
                        stamps.forEach { validateAsAgent(agent, it) }

                        val filter = AgentActionsFilter(agent = agent.account.email)
                        val first = agentActionsService.findAgentActions(filter, 0, 2)
                        assertEquals(2, first.pageItems.size)
                        assertEquals(2, first.pageInfo.nextPage?.offset)
                        val second = agentActionsService.findAgentActions(filter, 2, 2)
                        assertEquals(1, second.pageItems.size)
                        assertEquals(null, second.pageInfo.nextPage, "No next page")

                        val ids = (first.pageItems + second.pageItems).map { it.id }
                        assertEquals(ids.sortedDescending(), ids, "Newest first")
                        assertEquals(3, ids.toSet().size)

                        // A size beyond the cap is accepted, and capped
                        assertEquals(3, agentActionsService.findAgentActions(filter, 0, 1000).pageItems.size)
                    }
                }
            }
        }
    }

    @Test
    fun `The actions are restricted to the projects the caller can see`() {
        val (owner, agent) = ownerAndAgent()
        withNoGrantViewToAll {
            val visible = projectValidatedBy(agent)
            val hidden = projectValidatedBy(agent)
            // The admin sees both
            asAdmin {
                assertEquals(
                    setOf(visible.project.name, hidden.project.name),
                    actions(agent).projects().toSet(),
                )
            }
            // The owner sees only the first one
            asConfigurableAccount(owner).withView(visible).call {
                assertEquals(listOf(visible.project.name), actions(agent).projects())
                // Even when asking for the hidden project
                assertEquals(emptyList(), actions(agent, project = hidden.project.name))
            }
        }
    }

    @Test
    fun `An event linking a project the caller cannot see is left out`() {
        val (owner, agent) = ownerAndAgent()
        withNoGrantViewToAll {
            val (target, source) = asAdmin {
                val target = project<Build> { branch<Build> { build() } }
                val source = project<Build> { branch<Build> { build() } }
                // The agent links the hidden source build to the visible target build
                asAgent(agent) { source.linkTo(target) }
                target to source
            }
            asAdmin {
                assertEquals(
                    listOf(EventFactory.NEW_BUILD_LINK.id),
                    actions(agent).map { it.eventType.id },
                )
            }
            asConfigurableAccount(owner).withView(target).call {
                assertEquals(emptyList(), actions(agent), "Link from ${source.project.name} left out")
            }
        }
    }

    @Test
    fun `The events without a project are visible to the administrators only`() {
        val (owner, agent) = ownerAndAgent()
        withNoGrantViewToAll {
            val project = asAdmin { project() }
            // The agent deletes the project: the event has no project any longer
            asAgent(agent) { structureService.deleteProject(project.id) }
            asAdmin {
                assertEquals(
                    listOf(EventFactory.DELETE_PROJECT.id),
                    actions(agent).map { it.eventType.id },
                )
            }
            asFixedAccount(owner) {
                assertEquals(emptyList(), actions(agent))
            }
        }
    }

    @Test
    fun `The window and the filters`() {
        val (_, agent) = ownerAndAgent()
        asAdmin {
            val one = project<Build> {
                branch<Build> {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        validateAsAgent(agent, vs)
                        promoteAsAgent(agent, pl)
                    }
                }
            }
            val two = projectValidatedBy(agent)
            val now = LocalDateTime.now()

            // The window
            assertEquals(3, actions(agent, from = now.minusDays(7)).size)
            assertEquals(emptyList(), actions(agent, from = now.plusDays(1)), "Nothing in the future")
            assertEquals(emptyList(), actions(agent, to = now.minusDays(1)), "Nothing before yesterday")

            // The event types
            assertEquals(
                listOf(EventFactory.NEW_PROMOTION_RUN.id),
                actions(agent, eventTypes = listOf(EventFactory.NEW_PROMOTION_RUN.id)).map { it.eventType.id },
            )
            assertEquals(
                2,
                actions(agent, eventTypes = listOf(EventFactory.NEW_VALIDATION_RUN.id)).size,
            )

            // The project
            assertEquals(
                setOf(one.project.name),
                actions(agent, project = one.project.name).projects().toSet(),
            )
            assertEquals(
                listOf(two.project.name),
                actions(agent, project = two.project.name, eventTypes = listOf(EventFactory.NEW_VALIDATION_RUN.id)).projects(),
            )
        }
    }

    @Test
    fun `Licence off - the read is refused`() {
        val (_, agent) = ownerAndAgent()
        asAdmin {
            withoutAgentsLicence {
                val ex = assertFailsWith<LicenseFeatureException> {
                    actions(agent)
                }
                assertEquals("Feature not allowed by the license: extension.agents", ex.message)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The activity of an agent
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `The owner and the administrators read the activity of an agent, nobody else`() {
        val (owner, agent) = ownerAndAgent()
        withGrantViewToAll {
            val build = projectValidatedBy(agent)
            // The owner
            asFixedAccount(owner) {
                assertEquals(1, agentActionsService.getAgentActivity(agent.account.id, AgentActivityFilter(), 0, 10).pageItems.size)
            }
            // An administrator
            asAdmin {
                assertEquals(1, agentActionsService.getAgentActivity(agent.account.id, AgentActivityFilter(), 0, 10).pageItems.size)
            }
            // Anybody else, even if they see the project
            asUserWithView(build).call {
                assertFailsWith<AccessDeniedException> {
                    agentActionsService.getAgentActivity(agent.account.id, AgentActivityFilter(), 0, 10)
                }
            }
        }
    }

    @Test
    fun `Licence off - the activity of an agent is refused`() {
        val (owner, agent) = ownerAndAgent()
        withoutAgentsLicence {
            asFixedAccount(owner) {
                assertFailsWith<LicenseFeatureException> {
                    agentActionsService.getAgentActivity(agent.account.id, AgentActivityFilter(), 0, 10)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // GraphQL
    // ---------------------------------------------------------------------------------------------

    private val query = """
        query AgentActivity(
            ${'$'}id: Int!,
            ${'$'}from: LocalDateTime,
            ${'$'}eventTypes: [String!],
            ${'$'}project: String,
            ${'$'}offset: Int,
            ${'$'}size: Int,
        ) {
            agents(id: ${'$'}id) {
                agentActivity(from: ${'$'}from, eventTypes: ${'$'}eventTypes, project: ${'$'}project, offset: ${'$'}offset, size: ${'$'}size) {
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
                        message
                        project {
                            name
                        }
                        actor {
                            agent
                            sessionLink
                        }
                    }
                }
            }
        }
    """

    private fun activity(
        agent: AgentTestSupport.TestAgent,
        from: LocalDateTime? = null,
        eventTypes: List<String>? = null,
        project: String? = null,
    ): JsonNode? =
        run(
            query,
            mapOf(
                "id" to agent.account.id(),
                "from" to from?.toString(),
                "eventTypes" to eventTypes,
                "project" to project,
                "offset" to 0,
                "size" to 10,
            )
        ).path("agents").firstOrNull()?.path("agentActivity")

    @Test
    fun `GraphQL - the owner reads the activity of their agent`() {
        val (owner, agent) = ownerAndAgent()
        withGrantViewToAll {
            val build = projectValidatedBy(agent)
            asFixedAccount(owner) {
                val activity = activity(agent, from = LocalDateTime.now().minusDays(7))
                    ?: error("The owner sees their agent")
                val items = activity.path("pageItems")
                assertEquals(1, items.size())
                val item = items.first()
                assertEquals(EventFactory.NEW_VALIDATION_RUN.id, item.path("eventType").path("id").asString())
                assertEquals(build.project.name, item.path("project").path("name").asString())
                assertEquals(agent.account.email, item.path("actor").path("agent").asString())
                assertEquals("https://claude.ai/code/session-1", item.path("actor").path("sessionLink").asString())
                assertTrue(item.path("message").asString().contains(build.name), "Message rendered with the build")
                assertTrue(activity.path("pageInfo").path("nextPage").isNull, "No next page")

                // Filtered on another event type
                assertEquals(
                    0,
                    activity(agent, eventTypes = listOf(EventFactory.NEW_PROMOTION_RUN.id))?.path("pageItems")?.size()
                )
                // Filtered on the project
                assertEquals(
                    1,
                    activity(agent, project = build.project.name)?.path("pageItems")?.size()
                )
            }
        }
    }

    @Test
    fun `GraphQL - licence off is an error`() {
        val (owner, agent) = ownerAndAgent()
        withoutAgentsLicence {
            asFixedAccount(owner) {
                runWithError(
                    query,
                    mapOf("id" to agent.account.id()),
                    errorMessage = "Feature not allowed by the license: extension.agents",
                )
            }
        }
    }

    @Test
    fun `GraphQL - a person has no agent activity`() {
        val owner = asAdmin { doCreateAccount() }
        asAdmin {
            run(
                """
                    query Activity(${'$'}id: Int!) {
                        accounts(id: ${'$'}id) {
                            agentActivity {
                                pageItems { id }
                            }
                        }
                    }
                """,
                mapOf("id" to owner.id())
            ).let { data ->
                assertEquals(0, data.path("accounts").first().path("agentActivity").path("pageItems").size())
            }
        }
    }
}
