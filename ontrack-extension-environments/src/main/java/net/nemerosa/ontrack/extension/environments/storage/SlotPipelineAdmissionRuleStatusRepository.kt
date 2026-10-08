package net.nemerosa.ontrack.extension.environments.storage

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.readLocalDateTimeNotNull
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class SlotPipelineAdmissionRuleStatusRepository(
    dataSource: DataSource,
    private val slotAdmissionRuleConfigRepository: SlotAdmissionRuleConfigRepository,
) : AbstractJdbcRepository(dataSource) {

    fun saveStatus(slotPipelineAdmissionRuleStatus: SlotPipelineAdmissionRuleStatus) {
        namedParameterJdbcTemplate!!.update(
            """
                    INSERT INTO ENV_SLOT_PIPELINE_ADMISSION_RULE_STATUS(PIPELINE_ID, ADMISSION_RULE_CONFIG_ID, DATA, DATA_USER, DATA_TIMESTAMP, DATA_ACTOR, OVERRIDE_USER, OVERRIDE_TIMESTAMP, OVERRIDE_MESSAGE, OVERRIDE_ACTOR)
                    VALUES(:pipelineId, :admissionRuleConfigId, CAST(:data AS JSONB), :dataUser, :dataTimestamp, CAST(:dataActor AS JSONB), :overrideUser, :overrideTimestamp, :overrideMessage, CAST(:overrideActor AS JSONB))
                    ON CONFLICT (PIPELINE_ID, ADMISSION_RULE_CONFIG_ID) DO UPDATE SET
                        DATA = CAST(:data AS JSONB),
                        DATA_USER = :dataUser,
                        DATA_TIMESTAMP = :dataTimestamp,
                        DATA_ACTOR = CAST(:dataActor AS JSONB),
                        OVERRIDE_USER = :overrideUser,
                        OVERRIDE_TIMESTAMP = :overrideTimestamp, 
                        OVERRIDE_MESSAGE = :overrideMessage,
                        OVERRIDE_ACTOR = CAST(:overrideActor AS JSONB)
                """.trimIndent(),
            mapOf(
                "pipelineId" to slotPipelineAdmissionRuleStatus.pipeline.id,
                "admissionRuleConfigId" to slotPipelineAdmissionRuleStatus.admissionRuleConfig.id,
                "data" to slotPipelineAdmissionRuleStatus.data?.data?.let { writeJson(it) },
                "dataUser" to slotPipelineAdmissionRuleStatus.data?.user,
                "dataTimestamp" to slotPipelineAdmissionRuleStatus.data?.timestamp?.let { Time.store(it) },
                "dataActor" to slotPipelineAdmissionRuleStatus.data?.actor?.let { writeJson(it) },
                "overrideUser" to slotPipelineAdmissionRuleStatus.override?.user,
                "overrideTimestamp" to slotPipelineAdmissionRuleStatus.override?.timestamp?.let { Time.store(it) },
                "overrideMessage" to slotPipelineAdmissionRuleStatus.override?.message,
                "overrideActor" to slotPipelineAdmissionRuleStatus.override?.actor?.let { writeJson(it) },
            )
        )
    }

    fun findStatusByPipelineAndAdmissionRuleConfig(
        pipeline: SlotPipeline,
        config: SlotAdmissionRuleConfig
    ): SlotPipelineAdmissionRuleStatus? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM env_slot_pipeline_admission_rule_status
                WHERE pipeline_id = :pipelineId
                AND admission_rule_config_id = :admissionRuleConfigId
            """.trimIndent(),
            mapOf(
                "pipelineId" to pipeline.id,
                "admissionRuleConfigId" to config.id,
            )
        ) { rs, _ ->
            toSlotPipelineAdmissionRuleStatus(rs, pipeline, config)
        }.firstOrNull()

    fun findStatusesByPipeline(pipeline: SlotPipeline): List<SlotPipelineAdmissionRuleStatus> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM env_slot_pipeline_admission_rule_status
                WHERE pipeline_id = :pipelineId
            """.trimIndent(),
            mapOf(
                "pipelineId" to pipeline.id,
            )
        ) { rs, _ ->
            toSlotPipelineAdmissionRuleStatus(rs, pipeline)
        }

    private fun toSlotPipelineAdmissionRuleStatus(
        rs: ResultSet,
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig? = null,
    ) = SlotPipelineAdmissionRuleStatus(
        pipeline = pipeline,
        admissionRuleConfig = admissionRuleConfig ?: slotAdmissionRuleConfigRepository.getAdmissionRuleConfigById(
            pipeline.slot,
            rs.getString("ADMISSION_RULE_CONFIG_ID")
        ),
        data = readJson(rs, "data")?.let { data ->
            SlotAdmissionRuleData(
                user = rs.getString("data_user"),
                timestamp = rs.readLocalDateTimeNotNull("data_timestamp"),
                data = data,
                actor = readSignatureActor(rs, "data_actor"),
            )
        },
        override = rs.getString("override_user")?.let { user ->
            SlotAdmissionRuleOverride(
                user = user,
                timestamp = rs.readLocalDateTimeNotNull("override_timestamp"),
                message = rs.getString("override_message"),
                actor = readSignatureActor(rs, "override_actor"),
            )
        },
    )

}