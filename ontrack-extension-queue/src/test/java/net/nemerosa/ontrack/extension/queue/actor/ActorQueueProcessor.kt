package net.nemerosa.ontrack.extension.queue.actor

import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Queue processor which records the actor and the signature its messages are processed with, and
 * which creates a build when asked to, signed by the caller like the ingestion does.
 */
@Component
class ActorQueueProcessor(
    private val securityService: SecurityService,
    private val structureService: StructureService,
) : QueueProcessor<ActorQueuePayload> {

    private val actors = ConcurrentHashMap<String, Actor>()
    private val signatures = ConcurrentHashMap<String, Signature>()
    private val builds = ConcurrentHashMap<String, Build>()

    /**
     * Actor the message identified by [id] was processed with, if processed.
     */
    fun actor(id: String): Actor? = actors[id]

    /**
     * Current signature when the message identified by [id] was processed, if processed.
     */
    fun signature(id: String): Signature? = signatures[id]

    /**
     * Build created by the message identified by [id], if any.
     */
    fun build(id: String): Build? = builds[id]

    override val id: String = "test-actor"

    override val payloadType: KClass<ActorQueuePayload> = ActorQueuePayload::class

    override fun isCancelled(payload: ActorQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: ActorQueuePayload): String = payload.id

    override fun process(payload: ActorQueuePayload, queueMetadata: QueueMetadata?) {
        if (payload.branchId != null) {
            val branch = structureService.getBranch(ID.of(payload.branchId))
            builds[payload.id] = structureService.newBuild(
                Build.of(branch, NameDescription.nd(payload.id, ""), Signature.of(payload.signedBy ?: "caller"))
            )
        }
        signatures[payload.id] = securityService.currentSignature
        securityService.currentActor?.let { actors[payload.id] = it }
    }
}

/**
 * @property id ID of the message
 * @property branchId Branch to create a build into, if any
 * @property signedBy User name of the signature of the build, supplied by the caller
 */
data class ActorQueuePayload(
    val id: String,
    val branchId: Int? = null,
    val signedBy: String? = null,
)
