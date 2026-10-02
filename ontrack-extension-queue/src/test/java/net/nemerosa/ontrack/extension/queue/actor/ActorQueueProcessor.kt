package net.nemerosa.ontrack.extension.queue.actor

import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Queue processor which records the actor its messages are processed with.
 */
@Component
class ActorQueueProcessor(
    private val securityService: SecurityService,
) : QueueProcessor<ActorQueuePayload> {

    private val actors = ConcurrentHashMap<String, Actor>()

    /**
     * Actor the message identified by [id] was processed with, if processed.
     */
    fun actor(id: String): Actor? = actors[id]

    override val id: String = "test-actor"

    override val payloadType: KClass<ActorQueuePayload> = ActorQueuePayload::class

    override fun isCancelled(payload: ActorQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: ActorQueuePayload): String = payload.id

    override fun process(payload: ActorQueuePayload, queueMetadata: QueueMetadata?) {
        securityService.currentActor?.let { actors[payload.id] = it }
    }
}

data class ActorQueuePayload(
    val id: String,
)
