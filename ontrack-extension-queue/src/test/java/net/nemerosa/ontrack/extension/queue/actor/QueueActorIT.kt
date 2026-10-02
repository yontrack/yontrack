package net.nemerosa.ontrack.extension.queue.actor

import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.Roles
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
 * The actor of a message is carried across the queue, to its processing.
 *
 * Not transactional: the messages are processed on the threads of the queue listeners.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueActorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueDispatcher: QueueDispatcher

    @Autowired
    private lateinit var actorQueueProcessor: ActorQueueProcessor

    @OptIn(ExperimentalTime::class)
    private fun processedActor(id: String): Actor {
        waitUntil("Message $id processed", interval = 500.milliseconds, timeout = 30.seconds) {
            actorQueueProcessor.actor(id) != null
        }
        return actorQueueProcessor.actor(id)!!
    }

    @Test
    fun `The actor of an account is carried across the queue`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = "pipeline")
        val id = uid("m-")
        asActor(account, actor) {
            queueDispatcher.dispatch(actorQueueProcessor, ActorQueuePayload(id), source = null)
        }
        assertEquals(actor, processedActor(id))
    }

    @Test
    fun `The actor of the system acting on behalf of an account is carried across the queue`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = "pipeline")
        val id = uid("m-")
        asActor(account, actor) {
            securityService.asAdmin("auto-versioning") {
                queueDispatcher.dispatch(actorQueueProcessor, ActorQueuePayload(id), source = null)
            }
        }
        assertEquals(Actor.system(reason = "auto-versioning", onBehalfOf = actor), processedActor(id))
    }
}
