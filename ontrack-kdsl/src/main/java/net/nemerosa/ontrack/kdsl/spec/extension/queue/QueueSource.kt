package net.nemerosa.ontrack.kdsl.spec.extension.queue

import tools.jackson.databind.JsonNode

/**
 * Contains information about the source of a queue message.
 */
data class QueueSource(
        val feature: String,
        val id: String,
        val data: JsonNode,
)
