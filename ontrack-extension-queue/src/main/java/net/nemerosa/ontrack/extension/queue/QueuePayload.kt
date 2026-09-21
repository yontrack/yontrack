package net.nemerosa.ontrack.extension.queue

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseInto
import java.util.*
import kotlin.reflect.KClass

@APIDescription("Payload for a message sent on a queue for processing")
class QueuePayload(
    @APIDescription("Unique ID of the message")
    val id: String,
    @APIDescription("ID of the target processor")
    val processor: String,
    @APIDescription("Identity of the user")
    val accountName: String,
    @APIDescription("Message body for the processor")
    val body: JsonNode,
) {
    fun <T : Any> parse(payloadType: KClass<out T>): T =
        body.parseInto(payloadType)

    companion object {
        fun <T : Any> create(
            processor: QueueProcessor<T>,
            accountName: String,
            body: T,
        ) =
            QueuePayload(
                id = UUID.randomUUID().toString(),
                processor = processor.id,
                accountName = accountName,
                body = body.asJson()
            )

        fun parse(body: JsonNode) = body.parse<QueuePayload>()
    }
}
