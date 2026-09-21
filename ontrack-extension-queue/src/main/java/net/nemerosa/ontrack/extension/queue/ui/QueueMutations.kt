package net.nemerosa.ontrack.extension.queue.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatchResult
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.source.PostQueueSourceExtension
import net.nemerosa.ontrack.extension.queue.source.createQueueSource
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component

@Component
class QueueMutations(
        private val queueDispatcher: QueueDispatcher,
        private val securityService: SecurityService,
        private val postQueueSourceExtension: PostQueueSourceExtension,
        queueProcessors: List<QueueProcessor<*>>,
) : TypedMutationProvider() {

    private val queueProcessorIndex = queueProcessors.associateBy { it.id }

    override val mutations: List<Mutation> = listOf(
            simpleMutation(
                    name = "postQueue",
                    description = "Post a message on a queue",
                    input = PostQueueInput::class,
                    outputName = "queueDispatchResult",
                    outputDescription = "Result of the post",
                    outputType = QueueDispatchResult::class,
            ) { input ->
                securityService.checkGlobalFunction(ApplicationManagement::class.java)
                val processor = queueProcessorIndex[input.processor]
                        ?: error("Could not find queue processor with ID = ${input.processor}")
                dispatch(processor, input.payload)
            }
    )

    private fun <T : Any> dispatch(processor: QueueProcessor<T>, payload: JsonNode): QueueDispatchResult {
        val typedPayload = payload.parseInto(processor.payloadType)
        return queueDispatcher.dispatch(processor, typedPayload,
                source = postQueueSourceExtension.createQueueSource("")
        )
    }
}