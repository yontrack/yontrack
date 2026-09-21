package net.nemerosa.ontrack.extension.workflows.engine

import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import java.time.Duration
import java.time.LocalDateTime

/**
 * Execution information about a node
 *
 * @property id ID of the node
 */
data class WorkflowInstanceNode(
    val id: String,
    val status: WorkflowInstanceNodeStatus,
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val output: JsonNode?,
    val error: String?,
) {

    @get:JsonIgnore
    val durationMs: Long by lazy {
        if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime).toMillis()
        } else {
            0
        }
    }

    fun start(time: LocalDateTime = Time.now) = WorkflowInstanceNode(
        id = id,
        status = WorkflowInstanceNodeStatus.STARTED,
        startTime = time,
        endTime = endTime,
        output = output,
        error = error,
    )

    fun stop(time: LocalDateTime = Time.now) = WorkflowInstanceNode(
        id = id,
        status = WorkflowInstanceNodeStatus.CANCELLED,
        startTime = time,
        endTime = endTime,
        output = output,
        error = error,
    )

    fun success(output: JsonNode, time: LocalDateTime = Time.now) = WorkflowInstanceNode(
        id = id,
        status = WorkflowInstanceNodeStatus.SUCCESS,
        startTime = startTime,
        endTime = time,
        output = output,
        error = null,
    )

    fun progress(output: JsonNode) = WorkflowInstanceNode(
        id = id,
        status = status,
        startTime = startTime,
        endTime = endTime,
        output = output,
        error = null,
    )

    fun error(throwable: Throwable?, message: String?, output: JsonNode?, time: LocalDateTime = Time.now) = WorkflowInstanceNode(
        id = id,
        status = WorkflowInstanceNodeStatus.ERROR,
        startTime = startTime,
        endTime = time,
        output = output ?: this.output,
        error = message ?: throwable?.message ?: "Unknown error in $id node",
    )
}
