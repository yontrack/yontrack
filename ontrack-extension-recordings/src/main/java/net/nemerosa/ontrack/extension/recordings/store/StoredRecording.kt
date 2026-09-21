package net.nemerosa.ontrack.extension.recordings.store

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

data class StoredRecording(
        val id: String,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime?,
        val data: JsonNode,
)