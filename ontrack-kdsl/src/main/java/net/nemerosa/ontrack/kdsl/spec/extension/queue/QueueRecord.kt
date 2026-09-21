package net.nemerosa.ontrack.kdsl.spec.extension.queue

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

data class QueueRecord(
        val state: QueueRecordState,
        val queuePayload: QueuePayload,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime?,
        val routingKey: String?,
        val queueName: String?,
        val actualPayload: JsonNode?,
        val exception: String?,
        val history: List<QueueRecordHistory>,
        val source: QueueSource?,
)
