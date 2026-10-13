package net.nemerosa.ontrack.extension.tfc.queue

import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.extension.tfc.TFCExtensionFeature
import net.nemerosa.ontrack.extension.tfc.service.RunPayload
import net.nemerosa.ontrack.extension.tfc.service.TFCService
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class TFCQueueProcessor(
        extension: TFCExtensionFeature,
        private val tfcService: TFCService,
) : AbstractExtension(extension), QueueProcessor<RunPayload> {

    override val id: String = "tfc"

    // Events of a workspace are processed in order, whatever the number of backends
    override val singleActiveConsumer: Boolean = true

    override fun getRoutingIdentifier(payload: RunPayload): String = payload.workspaceName

    override val payloadType: KClass<RunPayload> = RunPayload::class

    override fun isCancelled(payload: RunPayload): String? = null

    override fun process(payload: RunPayload, queueMetadata: QueueMetadata?) {
        val status = when (payload.trigger) {
            "run:completed" -> when (payload.runStatus) {
                "applied" -> ValidationRunStatusID.STATUS_PASSED
                else -> ValidationRunStatusID.STATUS_WARNING
            }

            "run:errored" -> ValidationRunStatusID.STATUS_FAILED
            else -> ValidationRunStatusID.STATUS_WARNING
        }
        tfcService.validate(
                params = payload.parameters,
                status = status,
                workspaceId = payload.workspaceId,
                runUrl = payload.runUrl,
        )
    }

}