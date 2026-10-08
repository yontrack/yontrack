package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.license.control.LicenseFeatureException
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeProperty
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangePropertyType
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.events.SimpleEventType
import net.nemerosa.ontrack.model.events.eventContext
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The counts of the agent activity widget (#2035): aggregates over the events of the agents and over the
 * assisted change of the builds, restricted to the projects the caller can see.
 */
class AgentActivityStatsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Autowired
    private lateinit var agentActivityStatsService: AgentActivityStatsService

    @Autowired
    private lateinit var eventPostService: EventPostService

    private fun <T> withoutAgentsLicence(code: () -> T): T {
        devLicenseService.setFeatureEnabled(FEATURE_AGENTS, false)
        return try {
            code()
        } finally {
            devLicenseService.setFeatureEnabled(FEATURE_AGENTS, true)
        }
    }

    private fun agent(): AgentTestSupport.TestAgent {
        val owner = asAdmin { doCreateAccount() }
        return agentTestSupport.registerAgent(owner = owner)
    }

    /**
     * Runs the [code] as the [agent], the system acting on its behalf: the agent policy is not the point
     * here.
     */
    private fun <T> asAgent(agent: AgentTestSupport.TestAgent, code: () -> T): T =
        agentTestSupport.withToken(agent.token, sessionId = "session-1") {
            securityService.asAdmin(code)
        }

    private fun Branch.buildAsAgent(agent: AgentTestSupport.TestAgent): Build =
        asAgent(agent) { build() }

    private fun Build.promoteAsAgent(agent: AgentTestSupport.TestAgent, pl: PromotionLevel) {
        asAgent(agent) {
            structureService.newPromotionRun(
                PromotionRun.of(this, pl, securityService.currentSignature, null)
            )
        }
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

    /**
     * An event of the environments extension, on a project - without the environments themselves.
     */
    private fun Project.pipelineEventAsAgent(agent: AgentTestSupport.TestAgent, type: String) {
        asAgent(agent) {
            eventPostService.post(
                Event.of(SimpleEventType(type, "Pipeline", "Pipeline", eventContext()))
                    .withProject(this)
                    .build()
            )
        }
    }

    private fun Build.assistedChange(basis: AssistedChangeBasis, vararg assistants: String) {
        setProperty(
            this,
            AssistedChangePropertyType::class.java,
            if (basis == AssistedChangeBasis.UNKNOWN) {
                AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_SCM)
            } else {
                AssistedChangeProperty(
                    basis = basis,
                    assistants = assistants.toList(),
                    assistedCommits = if (assistants.isEmpty()) 0 else 1,
                    totalCommits = 2,
                )
            }
        )
    }

    private fun stats(
        window: Int = 30,
        projects: List<String> = emptyList(),
        labels: List<String> = emptyList(),
    ) = agentActivityStatsService.getAgentActivityStats(
        AgentActivityStatsFilter(window = window, projects = projects, labels = labels)
    )

    @Test
    fun `Builds, promotions and deployments by agents, not by persons`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val vs = validationStamp()
                    // Two builds by the agent, one by a person
                    val one = buildAsAgent(agent)
                    buildAsAgent(agent)
                    val byPerson = build()
                    // The agent promotes one build, a person the other one
                    one.promoteAsAgent(agent, pl)
                    byPerson.promote(pl)
                    // The agent validates: not counted
                    one.validateAsAgent(agent, vs)
                    // Deployments: the pipeline is started, starts its deployment and is deployed
                    project.pipelineEventAsAgent(agent, "slot-pipeline-creation")
                    project.pipelineEventAsAgent(agent, "slot-pipeline-deploying")
                    project.pipelineEventAsAgent(agent, "slot-pipeline-deployed")
                    // Not a deployment
                    project.pipelineEventAsAgent(agent, "slot-pipeline-cancelled")

                    val stats = stats(projects = listOf(project.name))
                    assertEquals(30, stats.window)
                    assertEquals(2, stats.builds)
                    assertEquals(1, stats.promotions)
                    assertEquals(3, stats.deployments)
                }
            }
        }
    }

    @Test
    fun `The assisted share excludes the unknown builds, counted apart`() {
        asAdmin {
            project {
                branch {
                    build().assistedChange(AssistedChangeBasis.COMPUTED, "Claude Code")
                    build().assistedChange(AssistedChangeBasis.SET_BY_CI, "Copilot")
                    build().assistedChange(AssistedChangeBasis.COMPUTED)
                    build().assistedChange(AssistedChangeBasis.SET_BY_CI)
                    build().assistedChange(AssistedChangeBasis.UNKNOWN)
                    build().assistedChange(AssistedChangeBasis.UNKNOWN)
                    // Not computed yet: in no count
                    build()

                    val stats = stats(projects = listOf(project.name))
                    assertEquals(2, stats.assistedBuilds)
                    assertEquals(4, stats.knownBuilds)
                    assertEquals(2, stats.unknownBuilds)
                    assertEquals(0.5, stats.assistedShare)
                }
            }
        }
    }

    @Test
    fun `No assisted share without any known build`() {
        asAdmin {
            project {
                branch {
                    build().assistedChange(AssistedChangeBasis.UNKNOWN)
                    val stats = stats(projects = listOf(project.name))
                    assertEquals(0, stats.knownBuilds)
                    assertEquals(1, stats.unknownBuilds)
                    assertNull(stats.assistedShare)
                }
            }
        }
    }

    @Test
    fun `A hidden project is not counted`() {
        val agent = agent()
        val user = asAdmin { doCreateAccount() }
        withNoGrantViewToAll {
            val visible = asAdmin {
                project {
                    branch {
                        buildAsAgent(agent).assistedChange(AssistedChangeBasis.COMPUTED, "Claude Code")
                    }
                }
            }
            val hidden = asAdmin {
                project {
                    branch {
                        buildAsAgent(agent).assistedChange(AssistedChangeBasis.COMPUTED, "Claude Code")
                        buildAsAgent(agent).assistedChange(AssistedChangeBasis.COMPUTED)
                    }
                }
            }
            asConfigurableAccount(user).withView(visible).call {
                val stats = stats()
                assertEquals(1, stats.builds, "Only the build of the visible project")
                assertEquals(1, stats.assistedBuilds)
                assertEquals(1, stats.knownBuilds)
                // Even when asking for it
                val narrowed = stats(projects = listOf(hidden.name))
                assertEquals(0, narrowed.builds)
                assertEquals(0, narrowed.knownBuilds)
            }
            asAdmin {
                val stats = stats(projects = listOf(visible.name, hidden.name))
                assertEquals(3, stats.builds)
                assertEquals(2, stats.assistedBuilds)
                assertEquals(3, stats.knownBuilds)
            }
        }
    }

    @Test
    fun `An action linking a hidden project is not counted`() {
        val agent = agent()
        val user = asAdmin { doCreateAccount() }
        withNoGrantViewToAll {
            val (target, _) = asAdmin {
                val target = project<Build> { branch<Build> { build() } }
                val source = project<Build> { branch<Build> { build() } }
                // An action of the agent on the target, with the hidden source as extra project
                asAgent(agent) {
                    eventPostService.post(
                        Event.of(SimpleEventType("slot-pipeline-deployed", "Pipeline", "Pipeline", eventContext()))
                            .withProject(target.project)
                            .withExtra(source.project)
                            .build()
                    )
                }
                target to source
            }
            asConfigurableAccount(user).withView(target).call {
                assertEquals(0, stats(projects = listOf(target.project.name)).deployments)
            }
            asAdmin {
                assertEquals(1, stats(projects = listOf(target.project.name)).deployments)
            }
        }
    }

    @Test
    fun `The window`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    buildAsAgent(agent)
                    assertEquals(1, stats(window = 7, projects = listOf(project.name)).builds)
                    // A window is at least one day, and at most a year
                    assertEquals(1, stats(window = 0, projects = listOf(project.name)).window)
                    assertEquals(366, stats(window = 10_000, projects = listOf(project.name)).window)
                }
            }
        }
    }

    @Test
    fun `Narrowed by labels`() {
        val agent = agent()
        asAdmin {
            val label = label()
            val labelled = project {
                branch { buildAsAgent(agent) }
            }
            projectLabelManagementService.associateProjectToLabel(labelled, label)
            val other = project {
                branch { buildAsAgent(agent) }
            }
            val stats = stats(labels = listOf(label.getDisplay()))
            assertEquals(1, stats.builds)
            // Labels and projects together
            assertEquals(0, stats(labels = listOf(label.getDisplay()), projects = listOf(other.name)).builds)
        }
    }

    @Test
    fun `Licence off - the counts are refused`() {
        asAdmin {
            withoutAgentsLicence {
                val ex = assertFailsWith<LicenseFeatureException> {
                    stats()
                }
                assertEquals("Feature not allowed by the license: extension.agents", ex.message)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // GraphQL
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `GraphQL - the counts`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    buildAsAgent(agent).assistedChange(AssistedChangeBasis.COMPUTED, "Claude Code")
                    build().assistedChange(AssistedChangeBasis.COMPUTED)
                    build().assistedChange(AssistedChangeBasis.UNKNOWN)
                    run(
                        """
                            query Stats(${'$'}projects: [String!]) {
                                agentActivityStats(window: 7, projects: ${'$'}projects) {
                                    window
                                    from
                                    builds
                                    promotions
                                    deployments
                                    assistedBuilds
                                    knownBuilds
                                    unknownBuilds
                                    assistedShare
                                }
                            }
                        """,
                        mapOf("projects" to listOf(project.name))
                    ).path("agentActivityStats").let { stats ->
                        assertEquals(7, stats.path("window").asInt())
                        assertEquals(1, stats.path("builds").asInt())
                        assertEquals(0, stats.path("promotions").asInt())
                        assertEquals(0, stats.path("deployments").asInt())
                        assertEquals(1, stats.path("assistedBuilds").asInt())
                        assertEquals(2, stats.path("knownBuilds").asInt())
                        assertEquals(1, stats.path("unknownBuilds").asInt())
                        assertEquals(0.5, stats.path("assistedShare").asDouble())
                    }
                }
            }
        }
    }

    @Test
    fun `GraphQL - the latest agent actions`() {
        val agent = agent()
        val user = asAdmin { doCreateAccount() }
        withNoGrantViewToAll {
            val visible = asAdmin { project { branch { buildAsAgent(agent) } } }
            val hidden = asAdmin { project { branch { buildAsAgent(agent) } } }
            val query = """
                query Actions(${'$'}agent: String, ${'$'}eventTypes: [String!]) {
                    agentActions(agent: ${'$'}agent, eventTypes: ${'$'}eventTypes, size: 100) {
                        pageItems {
                            eventType { id }
                            project { name }
                            actor { agent }
                        }
                    }
                }
            """
            asConfigurableAccount(user).withView(visible).call {
                val items = run(query, mapOf("agent" to agent.account.email))
                    .path("agentActions").path("pageItems")
                assertEquals(1, items.size())
                assertEquals(visible.name, items.first().path("project").path("name").asString())
                assertEquals(EventFactory.NEW_BUILD.id, items.first().path("eventType").path("id").asString())
                assertEquals(agent.account.email, items.first().path("actor").path("agent").asString())
                // Filtered on the event type
                assertEquals(
                    0,
                    run(query, mapOf("agent" to agent.account.email, "eventTypes" to listOf(EventFactory.NEW_PROMOTION_RUN.id)))
                        .path("agentActions").path("pageItems").size()
                )
            }
            asAdmin {
                val items = run(query, mapOf("agent" to agent.account.email)).path("agentActions").path("pageItems")
                val projects: Set<String> = (0 until items.size()).map { index ->
                    items.path(index).path("project").path("name").asString()
                }.toSet()
                assertEquals(setOf(visible.name, hidden.name), projects)
            }
        }
    }

    @Test
    fun `GraphQL - licence off is an error`() {
        asAdmin {
            withoutAgentsLicence {
                runWithError(
                    "{ agentActivityStats { builds } }",
                    errorMessage = "Feature not allowed by the license: extension.agents",
                )
                runWithError(
                    "{ agentActions { pageItems { id } } }",
                    errorMessage = "Feature not allowed by the license: extension.agents",
                )
            }
        }
    }
}
