package net.nemerosa.ontrack.extension.queue.ui

import tools.jackson.databind.JsonNode

data class PostQueueInput(
        val processor: String,
        val payload: JsonNode,
)
