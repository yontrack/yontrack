package net.nemerosa.ontrack.extension.queue.record

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.queue.QueuePayload
import net.nemerosa.ontrack.extension.queue.source.QueueSource
import net.nemerosa.ontrack.extension.recordings.Recording
import net.nemerosa.ontrack.model.annotations.APIIgnore
import java.time.Duration
import java.time.LocalDateTime

@APIDescription("Recording a queuing event.")
data class QueueRecord(
    @APIDescription("State of the queuing")
    val state: QueueRecordState,
    @APIDescription("Payload for the queue message")
    val queuePayload: QueuePayload,
    @APIDescription("Time of creation")
    @APIIgnore("Managed by the top recordings record")
    override val startTime: LocalDateTime,
    @APIDescription("End of processing")
    @APIIgnore("Managed by the top recordings record")
    override val endTime: LocalDateTime?,
    @APIDescription("Queue routing key being used")
    val routingKey: String?,
    @APIDescription("Queue where the message was stored")
    val queueName: String?,
    @APIDescription("Actual payload sent to the processing")
    val actualPayload: JsonNode?,
    @APIDescription("Error on processing")
    val exception: String?,
    @APIDescription("History of the states")
    val history: List<QueueRecordHistory>,
    @APIDescription("Source of the message")
    val source: QueueSource?,
    @APIDescription("Username attached to the queue message (admin for administrative account, user for anything else")
    val username: String?,
) : Recording {

    @Suppress("unused")
    @APIDescription("Duration in ms")
    val durationSeconds: Int = if (endTime != null) {
        Duration.between(startTime, endTime).toSeconds().toInt()
    } else {
        -1
    }

    override val id: String = queuePayload.id

    fun withState(state: QueueRecordState) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = listOf(
            QueueRecordHistory(
                state = state,
                time = Time.now()
            )
        ) + history,
        source = source,
        username = username,
    )

    fun withRoutingKey(routingKey: String) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = history,
        source = source,
        username = username,
    )

    fun withQueue(queueName: String?) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = history,
        source = source,
        username = username,
    )

    fun withActualPayload(actualPayload: JsonNode) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = history,
        source = source,
        username = username,
    )

    fun withException(exception: String) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = history,
        source = source,
        username = username,
    )

    fun withEndTime(endTime: LocalDateTime) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = history,
        source = source,
        username = username,
    )

    fun withStartTime(startTime: LocalDateTime) = QueueRecord(
        state = state,
        queuePayload = queuePayload,
        startTime = startTime,
        endTime = endTime,
        routingKey = routingKey,
        queueName = queueName,
        actualPayload = actualPayload,
        exception = exception,
        history = history,
        source = source,
        username = username,
    )

    companion object {
        fun create(queuePayload: QueuePayload, source: QueueSource?, username: String?): QueueRecord {
            val time = Time.now()
            return QueueRecord(
                state = QueueRecordState.STARTED,
                queuePayload = queuePayload,
                startTime = time,
                endTime = null,
                routingKey = null,
                queueName = null,
                actualPayload = null,
                exception = null,
                history = listOf(
                    QueueRecordHistory(
                        state = QueueRecordState.STARTED,
                        time = time,
                    )
                ),
                source = source,
                username = username,
            )
        }
    }
}