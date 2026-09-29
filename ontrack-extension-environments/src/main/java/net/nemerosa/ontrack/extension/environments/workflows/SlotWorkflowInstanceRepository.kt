package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleOverride
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.storage.SlotPipelineRepository
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.readLocalDateTimeNotNull
import net.nemerosa.ontrack.repository.support.readLocalDateTime
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime
import javax.sql.DataSource

@Repository
class SlotWorkflowInstanceRepository(
    dataSource: DataSource,
    private val slotPipelineRepository: SlotPipelineRepository,
    private val slotWorkflowRepository: SlotWorkflowRepository,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
) : AbstractJdbcRepository(dataSource) {

    fun addSlotWorkflowInstance(slotWorkflowInstance: SlotWorkflowInstance) {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO ENV_SLOT_WORKFLOW_INSTANCES(ID, START, PIPELINE_ID, SLOT_WORKFLOW_ID, WORKFLOW_INSTANCE_ID)
                 VALUES (:id, :start, :pipelineId, :slotWorkflowId, :workflowInstanceId)
            """,
            mapOf(
                "id" to slotWorkflowInstance.id,
                "start" to dateTimeForDB(slotWorkflowInstance.start),
                "pipelineId" to slotWorkflowInstance.pipeline.id,
                "slotWorkflowId" to slotWorkflowInstance.slotWorkflow.id,
                "workflowInstanceId" to slotWorkflowInstance.workflowInstance.id,
            )
        )
    }

    fun getSlotWorkflowInstancesByPipeline(
        pipeline: SlotPipeline,
    ): List<SlotWorkflowInstance> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_WORKFLOW_INSTANCES
                WHERE PIPELINE_ID = :pipelineId
                ORDER BY START DESC
            """,
            mapOf(
                "pipelineId" to pipeline.id
            )
        ) { rs, _ ->
            toSlotWorkflowInstance(rs)
        }

    fun findSlotWorkflowInstancesById(id: String): SlotWorkflowInstance? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_WORKFLOW_INSTANCES
                WHERE ID = :id
            """,
            mapOf(
                "id" to id
            )
        ) { rs, _ ->
            toSlotWorkflowInstance(rs)
        }.firstOrNull()

    private fun toSlotWorkflowInstance(rs: ResultSet): SlotWorkflowInstance {
        val workflowInstanceId = rs.getString("workflow_instance_id")
        return SlotWorkflowInstance(
            id = rs.getString("id"),
            start = rs.readLocalDateTimeNotNull("start"),
            pipeline = slotPipelineRepository.getPipelineById(rs.getString("pipeline_id")),
            slotWorkflow = slotWorkflowRepository.getSlotWorkflowById(rs.getString("slot_workflow_id")),
            workflowInstance = workflowInstanceRepository.findWorkflowInstance(workflowInstanceId)
                ?: error("Cannot find workflow instance with ID: $workflowInstanceId"),
            override = if (rs.getBoolean("override")) {
                SlotAdmissionRuleOverride(
                    user = rs.getString("user"),
                    timestamp = rs.readLocalDateTime("timestamp") ?: Time.now,
                    message = rs.getString("override_message")
                )
            } else {
                null
            },
        )
    }

    fun findSlotWorkflowInstanceByPipelineAndSlotWorkflow(
        pipeline: SlotPipeline,
        slotWorkflow: SlotWorkflow
    ): SlotWorkflowInstance? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOT_WORKFLOW_INSTANCES
                WHERE PIPELINE_ID = :pipelineId
                AND SLOT_WORKFLOW_ID = :slotWorkflowId
            """,
            mapOf(
                "pipelineId" to pipeline.id,
                "slotWorkflowId" to slotWorkflow.id,
            )
        ) { rs, _ ->
            toSlotWorkflowInstance(rs)
        }.firstOrNull()

    fun override(slotWorkflowInstance: SlotWorkflowInstance, user: String, timestamp: LocalDateTime, message: String) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE ENV_SLOT_WORKFLOW_INSTANCES
                SET "USER" = :user, TIMESTAMP = :timestamp, OVERRIDE = TRUE, OVERRIDE_MESSAGE = :message
                WHERE ID = :id
            """.trimIndent(),
            mapOf(
                "id" to slotWorkflowInstance.id,
                "user" to user,
                "timestamp" to dateTimeForDB(timestamp),
                "message" to message,
            )
        )
    }

}