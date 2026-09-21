package net.nemerosa.ontrack.kdsl.spec.extension.queue

import tools.jackson.databind.JsonNode

data class QueuePayload(
    val id: String,
    val processor: String,
    val body: JsonNode,
)
