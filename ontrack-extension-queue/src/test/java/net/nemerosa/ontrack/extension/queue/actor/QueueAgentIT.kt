package net.nemerosa.ontrack.extension.queue.actor

import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorAgent
import net.nemerosa.ontrack.model.security.ActorAgentSession
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.structure.SignatureActor
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * An agent's action through the queue, as the ingestion does it, keeps the agent and its session.
 *
 * Not transactional: the messages are processed on the threads of the queue listeners.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueAgentIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueDispatcher: QueueDispatcher

    @Autowired
    private lateinit var actorQueueProcessor: ActorQueueProcessor

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @OptIn(ExperimentalTime::class)
    private fun waitForProcessing(id: String) {
        waitUntil("Message $id processed", interval = 500.milliseconds, timeout = 30.seconds) {
            actorQueueProcessor.actor(id) != null
        }
    }

    @Test
    fun `An agent's message through the queue keeps the agent and its session, and signs as the agent`() {
        val branch = asAdmin { project().branch() }
        val agent = agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )
        val id = uid("m-")
        agentTestSupport.withToken(agent.token, SESSION_ID, SESSION_LINK) {
            // Like the ingestion hook, which queues its payloads as the system acting for the caller
            securityService.asAdmin("github-ingestion") {
                queueDispatcher.dispatch(
                    actorQueueProcessor,
                    ActorQueuePayload(id = id, branchId = branch.id(), signedBy = "github-login"),
                    source = null,
                )
            }
        }
        waitForProcessing(id)
        val session = ActorAgentSession(id = SESSION_ID, link = SESSION_LINK)
        // Actor of the processing
        assertEquals(
            Actor.system(
                reason = "github-ingestion",
                onBehalfOf = Actor(
                    account = agent.account.email,
                    via = ActorVia.TOKEN,
                    tokenName = "ci",
                    agent = ActorAgent(
                        name = agent.account.email,
                        displayName = "Claude",
                        tool = "Claude Code",
                        owner = agent.account.owner!!.email,
                    ),
                    agentSession = session,
                ),
            ),
            actorQueueProcessor.actor(id),
        )
        val expected = SignatureActor(
            agent = agent.account.email,
            displayName = "Claude",
            tool = "Claude Code",
            owner = agent.account.owner!!.email,
            session = session,
        )
        // Signature of the processing
        assertEquals(expected, actorQueueProcessor.signature(id)?.actor)
        // Build created with a signature supplied by the caller: its actor is still the agent
        val build = actorQueueProcessor.build(id)!!
        asAdmin {
            val loaded = structureService.getBuild(build.id)
            assertEquals("github-login", loaded.signature.user.name)
            assertEquals(expected, loaded.signature.actor)
        }
    }

    @Test
    fun `A person's message through the queue has no agent`() {
        val branch = asAdmin { project().branch() }
        val id = uid("m-")
        asAdmin {
            queueDispatcher.dispatch(
                actorQueueProcessor,
                ActorQueuePayload(id = id, branchId = branch.id(), signedBy = "github-login"),
                source = null,
            )
        }
        waitForProcessing(id)
        assertEquals(null, actorQueueProcessor.signature(id)?.actor)
        asAdmin {
            assertEquals(null, structureService.getBuild(actorQueueProcessor.build(id)!!.id).signature.actor)
        }
    }

    companion object {
        private const val SESSION_ID = "session-2025"
        private const val SESSION_LINK = "https://claude.ai/code/session-2025"
    }
}
