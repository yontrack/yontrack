package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The agent on the actor of the entries does not change the hashes of a person's entries: a trail
 * mixing entries of a person, as written before agents were recorded, and entries of an agent,
 * verifies.
 */
class AgentTrailListenerIT : AbstractTrailListenerITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Autowired
    private lateinit var trailVerificationService: TrailVerificationService

    @Test
    fun `A trail with entries of a person and of an agent verifies`() {
        val agent = agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )
        asAdmin {
            project {
                branch {
                    // A person creates the build: the actor is the one written before agents existed
                    val (build, personActor) = asCi {
                        structureService.newBuild(
                            Build.of(this, NameDescription.nd("1.0.0", ""), Signature.of("jenkins"))
                        )
                    }
                    // An agent edits it, with its session
                    agentTestSupport.withToken(agent.token, "session-1", "https://claude.ai/code/session-1") {
                        securityService.asAdmin {
                            structureService.saveBuild(build.withDescription("Edited by an agent"))
                        }
                    }
                    val entries = build.trail()
                    assertEquals(
                        listOf(TrailEntryTypes.BUILD_CREATED, TrailEntryTypes.BUILD_UPDATED),
                        entries.map { it.type },
                    )
                    // The person's actor is unchanged, to the byte of its canonical form
                    assertEquals(personActor, entries[0].actor)
                    assertEquals(
                        """{"account":"${personActor.path("account").asString()}","tokenName":"$ciTokenName","via":"token"}""",
                        entries[0].actor.toString(),
                    )
                    // The agent's actor carries the agent and its session
                    assertEquals(
                        """
                            {
                              "account": "${agent.account.email}",
                              "via": "token",
                              "tokenName": "ci",
                              "agent": {
                                "name": "${agent.account.email}",
                                "displayName": "Claude",
                                "tool": "Claude Code",
                                "owner": "${agent.account.owner!!.email}"
                              },
                              "agentSession": {"id": "session-1", "link": "https://claude.ai/code/session-1"}
                            }
                        """.parseAsJson(),
                        entries[1].actor.path("onBehalfOf"),
                    )
                    // The whole trail verifies
                    val verification = asAdmin { trailVerificationService.verify(build) }
                    assertTrue(verification.chainIntact, "Chain intact")
                    assertEquals(emptyList(), verification.problems)
                }
            }
        }
    }
}
