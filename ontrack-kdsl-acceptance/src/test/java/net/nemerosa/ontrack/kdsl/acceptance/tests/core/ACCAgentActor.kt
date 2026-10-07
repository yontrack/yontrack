package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.SignatureActor
import net.nemerosa.ontrack.kdsl.spec.admin.Account
import net.nemerosa.ontrack.kdsl.spec.admin.agents
import net.nemerosa.ontrack.kdsl.spec.admin.currentAccount
import net.nemerosa.ontrack.kdsl.spec.signature
import net.nemerosa.ontrack.kdsl.spec.withToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The actor on the record: the signature of what an agent does names the agent, its owner and its
 * session (#2025).
 */
class ACCAgentActor : AbstractACCDSLTestSupport() {

    private fun registerAgent(): Triple<Account, String, String> {
        val slug = uid("acc-").lowercase()
        lateinit var owner: Account
        lateinit var token: String
        withUser(globalRole = "AUTOMATION") { account ->
            owner = account
            token = ontrack.agents.register(
                slug = slug,
                displayName = "Claude",
                tool = "Claude Code",
            ).generateToken("ci")
        }
        return Triple(owner, "$slug[agent]", token)
    }

    @Test
    fun `The signature of a build created by a person has no actor`() {
        project {
            branch {
                build {
                    val signature = signature()
                    assertNotNull(signature.user)
                    assertNull(signature.actor)
                }
            }
        }
    }

    @Test
    fun `An agent token with session headers is accepted`() {
        val (_, agent, token) = registerAgent()
        val asAgent = ontrack.withToken(
            token = token,
            agentSession = "session-1",
            agentSessionLink = "https://claude.ai/code/session-1",
        )
        assertEquals(agent, asAgent.currentAccount()?.email)
    }

    @Test
    fun `A build created by an agent with session headers names the agent, its owner and its session`() {
        val (owner, agent, token) = registerAgent()
        project {
            branch {
                val asAgent = ontrack.withToken(
                    token = token,
                    agentSession = "session-1",
                    agentSessionLink = "https://claude.ai/code/session-1",
                )
                val branch = assertNotNull(asAgent.findBranchByName(project.name, name), "Agent sees the branch")
                val build = branch.createBuild(uid("b-"))
                val signature = build.signature()
                assertEquals(agent, signature.user)
                assertEquals(
                    SignatureActor(
                        kind = "agent",
                        agent = agent,
                        displayName = "Claude",
                        tool = "Claude Code",
                        owner = owner.email,
                        sessionId = "session-1",
                        sessionLink = "https://claude.ai/code/session-1",
                    ),
                    signature.actor,
                )
            }
        }
    }
}
