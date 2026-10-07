package net.nemerosa.ontrack.graphql.schema.agents

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Signature.actor` in GraphQL.
 */
class SignatureActorGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun creation(build: Build) = asAdmin {
        run(
            """
                {
                    build(id: ${build.id}) {
                        creation {
                            user
                            actor { kind agent displayName tool owner sessionId sessionLink }
                        }
                    }
                }
            """
        ).path("build").path("creation")
    }

    @Test
    fun `Signature actor of a build created by an agent`() {
        val branch = asAdmin { project().branch() }
        val agent = agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )
        val build = agentTestSupport.withToken(agent.token, "session-1", "https://claude.ai/code/session-1") {
            securityService.asAdmin {
                structureService.newBuild(
                    Build.of(branch, NameDescription.nd(uid("B"), ""), securityService.currentSignature)
                )
            }
        }
        assertEquals(
            """
                {
                    "user": "${agent.account.email}",
                    "actor": {
                        "kind": "agent",
                        "agent": "${agent.account.email}",
                        "displayName": "Claude",
                        "tool": "Claude Code",
                        "owner": "${agent.account.owner!!.email}",
                        "sessionId": "session-1",
                        "sessionLink": "https://claude.ai/code/session-1"
                    }
                }
            """.parseAsJson(),
            creation(build),
        )
    }

    @Test
    fun `No signature actor for a build created by a person`() {
        val build = asAdmin { project().branch().build() }
        assertTrue(creation(build).path("actor").isNull)
    }
}
