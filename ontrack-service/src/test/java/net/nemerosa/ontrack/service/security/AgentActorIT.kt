package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.ActorAgentSession
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The actor on the record: the `ACTOR` column of the signed tables carries the agent which signed
 * the row, with its session, and is null for a person.
 *
 * The agent policy is not the point here (see AgentPolicyIT): the actions of the agent are run by
 * the system on its behalf, which keeps them as the user and the actor of the signature.
 */
class AgentActorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun agent(): AgentTestSupport.TestAgent =
        agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )

    private fun <T> AgentTestSupport.TestAgent.act(code: () -> T): T =
        agentTestSupport.withToken(token, sessionId = SESSION_ID, sessionLink = SESSION_LINK) {
            securityService.asAdmin(code)
        }

    private fun AgentTestSupport.TestAgent.expectedActor() = SignatureActor(
        agent = account.email,
        displayName = "Claude",
        tool = "Claude Code",
        owner = account.owner!!.email,
        session = ActorAgentSession(id = SESSION_ID, link = SESSION_LINK),
    )

    private fun AgentTestSupport.TestAgent.expectedActorJson(): JsonNode =
        """
            {
              "kind": "agent",
              "agent": "${account.email}",
              "displayName": "Claude",
              "tool": "Claude Code",
              "owner": "${account.owner!!.email}",
              "session": {"id": "$SESSION_ID", "link": "$SESSION_LINK"}
            }
        """.parseAsJson()

    private fun actorColumn(sql: String, id: Int): JsonNode? =
        namedParameterJdbcTemplate.queryForList(sql, mapOf("id" to id), String::class.java)
            .single()
            ?.parseAsJson()

    private fun buildActor(build: Build) = actorColumn("SELECT ACTOR FROM BUILDS WHERE ID = :id", build.id())

    private fun promotionRunActor(run: PromotionRun) =
        actorColumn("SELECT ACTOR FROM PROMOTION_RUNS WHERE ID = :id", run.id())

    private fun validationRunStatusActors(run: ValidationRun): List<JsonNode?> =
        namedParameterJdbcTemplate.queryForList(
            "SELECT ACTOR FROM VALIDATION_RUN_STATUSES WHERE VALIDATIONRUNID = :id ORDER BY ID",
            mapOf("id" to run.id()),
            String::class.java
        ).map { it?.parseAsJson() }

    private fun eventActor(eventType: String, column: String, id: Int) =
        actorColumn("SELECT ACTOR FROM EVENTS WHERE $column = :id AND EVENT_TYPE = '$eventType'", id)

    private fun newBuild(branch: Branch, signature: Signature = securityService.currentSignature) =
        structureService.newBuild(Build.of(branch, NameDescription.nd(uid("B"), ""), signature))

    @Test
    fun `An agent creates a build`() {
        val branch = asAdmin { project().branch() }
        val agent = agent()
        val build = agent.act { newBuild(branch) }
        // Signature as returned
        assertEquals(agent.account.email, build.signature.user.name)
        assertEquals(agent.expectedActor(), build.signature.actor)
        // Columns
        assertEquals(agent.expectedActorJson(), buildActor(build))
        assertEquals(agent.expectedActorJson(), eventActor("new_build", "BUILD", build.id()))
        // Read back
        asAdmin {
            val loaded = structureService.getBuild(build.id)
            assertEquals(agent.account.email, loaded.signature.user.name)
            assertEquals(agent.expectedActor(), loaded.signature.actor)
        }
    }

    @Test
    fun `An agent creates a validation run and changes its status`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val run = agent.act {
                            val run = structureService.newValidationRun(
                                this,
                                ValidationRunRequest(
                                    validationStampName = vs.name,
                                    validationRunStatusId = ValidationRunStatusID.STATUS_FAILED,
                                )
                            )
                            structureService.newValidationRunStatus(
                                run,
                                ValidationRunStatus(
                                    id = ID.NONE,
                                    signature = securityService.currentSignature,
                                    statusID = ValidationRunStatusID.STATUS_INVESTIGATING,
                                    description = "Looking",
                                )
                            )
                        }
                        assertEquals(
                            listOf(agent.expectedActorJson(), agent.expectedActorJson()),
                            validationRunStatusActors(run),
                        )
                        assertEquals(
                            agent.expectedActorJson(),
                            eventActor("new_validation_run", "VALIDATION_RUN", run.id()),
                        )
                        // Read back
                        val loaded = structureService.getValidationRun(run.id)
                        loaded.validationRunStatuses.forEach { status ->
                            assertEquals(agent.account.email, status.signature.user.name)
                            assertEquals(agent.expectedActor(), status.signature.actor)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `An agent promotes a build`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    build {
                        val run = agent.act {
                            structureService.newPromotionRun(
                                PromotionRun.of(this, pl, securityService.currentSignature, null)
                            )
                        }
                        assertEquals(agent.expectedActorJson(), promotionRunActor(run))
                        assertEquals(
                            agent.expectedActorJson(),
                            eventActor("new_promotion_run", "PROMOTION_RUN", run.id()),
                        )
                        val loaded = structureService.getPromotionRun(run.id)
                        assertEquals(agent.account.email, loaded.signature.user.name)
                        assertEquals(agent.expectedActor(), loaded.signature.actor)
                    }
                }
            }
        }
    }

    @Test
    fun `A person leaves the actor null`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val vs = validationStamp()
                    val build = newBuild(this)
                    val promotionRun = structureService.newPromotionRun(
                        PromotionRun.of(build, pl, securityService.currentSignature, null)
                    )
                    val validationRun = structureService.newValidationRun(
                        build,
                        ValidationRunRequest(
                            validationStampName = vs.name,
                            validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                        )
                    )
                    assertNull(buildActor(build))
                    assertNull(eventActor("new_build", "BUILD", build.id()))
                    assertNull(promotionRunActor(promotionRun))
                    assertEquals(listOf<JsonNode?>(null), validationRunStatusActors(validationRun))
                    assertNull(structureService.getBuild(build.id).signature.actor)
                }
            }
        }
    }

    @Test
    fun `A caller supplied signature always takes the actor of the authenticated agent`() {
        val branch = asAdmin { project().branch() }
        val agent = agent()
        // Signature supplied by the caller, like the ingestion or a backdated build, claiming no agent
        val build = agent.act { newBuild(branch, Signature.of("github-login")) }
        assertEquals("github-login", build.signature.user.name)
        assertEquals(agent.expectedActorJson(), buildActor(build))
    }

    @Test
    fun `A caller supplied signature cannot claim an agent for a person`() {
        val branch = asAdmin { project().branch() }
        val claimed = SignatureActor(agent = "fake[agent]", displayName = "Fake", owner = "nobody@yontrack.test")
        val build = asAdmin { newBuild(branch, Signature.of("someone").withActor(claimed)) }
        assertNull(build.signature.actor)
        assertNull(buildActor(build))
    }

    @Test
    fun `An email of 60 characters signs a build`() {
        val branch = asAdmin { project().branch() }
        val email = uid("e-").let { prefix -> prefix + "x".repeat(60 - prefix.length - "@yontrack.test".length) } + "@yontrack.test"
        assertEquals(60, email.length)
        val build = asUser(email).withProjectFunction(branch, BuildCreate::class.java).call {
            newBuild(branch)
        }
        asAdmin {
            assertEquals(email, structureService.getBuild(build.id).signature.user.name)
        }
    }

    @Test
    fun `The JSON of a build signed by an agent`() {
        val branch = asAdmin { project().branch() }
        val agent = agent()
        val build = agent.act { newBuild(branch) }
        assertEquals(agent.expectedActorJson(), build.signature.asJson().path("actor"))
    }

    companion object {
        private const val SESSION_ID = "session-2025"
        private const val SESSION_LINK = "https://claude.ai/code/session-2025"
    }
}
