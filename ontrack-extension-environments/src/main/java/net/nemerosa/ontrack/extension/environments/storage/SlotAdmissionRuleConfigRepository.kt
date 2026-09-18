package net.nemerosa.ontrack.extension.environments.storage

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.service.SlotAdmissionRuleConfigIdNotFound
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class SlotAdmissionRuleConfigRepository(
    dataSource: DataSource,
    private val slotRepository: SlotRepository,
) : AbstractJdbcRepository(dataSource) {

    fun addAdmissionRuleConfig(config: SlotAdmissionRuleConfig) {
        namedParameterJdbcTemplate!!.update(
            """
                 INSERT INTO ENV_SLOT_ADMISSION_RULE_CONFIGS(ID, SLOT_ID, NAME, DESCRIPTION, RULE_ID, RULE_CONFIG)
                 VALUES (:id, :slotId, :name, :description, :ruleId, CAST(:ruleConfig AS JSONB))
            """,
            mapOf(
                "id" to config.id,
                "slotId" to config.slot.id,
                "name" to config.name,
                "description" to config.description,
                "ruleId" to config.ruleId,
                "ruleConfig" to writeJson(config.ruleConfig),
            )
        )
    }

    fun saveAdmissionRuleConfig(config: SlotAdmissionRuleConfig) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE ENV_SLOT_ADMISSION_RULE_CONFIGS
                SET NAME = :name, RULE_CONFIG = CAST(:ruleConfig AS JSONB), DESCRIPTION = :description
                WHERE ID = :id
                AND SLOT_ID = :slotId
            """,
            mapOf(
                "id" to config.id,
                "name" to config.name,
                "slotId" to config.slot.id,
                "description" to config.description,
                "ruleConfig" to writeJson(config.ruleConfig),
            )
        )
    }

    fun getAdmissionRuleConfigs(slot: Slot): List<SlotAdmissionRuleConfig> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                    FROM ENV_SLOT_ADMISSION_RULE_CONFIGS
                    WHERE SLOT_ID = :slotId
                    ORDER BY NAME
            """,
            mapOf(
                "slotId" to slot.id,
            )
        ) { rs, _ ->
            toSlotAdmissionRuleConfig(rs, slot)
        }

    private fun toSlotAdmissionRuleConfig(rs: ResultSet, slot: Slot) = SlotAdmissionRuleConfig(
        id = rs.getString("id"),
        slot = slot,
        name = rs.getString("name"),
        description = rs.getString("description"),
        ruleId = rs.getString("rule_id"),
        ruleConfig = readJson(rs, "rule_config")
    )

    fun deleteAdmissionRuleConfig(config: SlotAdmissionRuleConfig) {
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENV_SLOT_ADMISSION_RULE_CONFIGS
                 WHERE ID = :id
            """,
            mapOf("id" to config.id)
        )
    }

    /**
     * The configured rule with this id, or `null`.
     *
     * `query(...).firstOrNull()` rather than `queryForObject`: the latter *throws*
     * `EmptyResultDataAccessException` when nothing matches, so this method never returned the null
     * its own type promises. Every caller was written against that promise -
     * `deleteSlotAdmissionRuleConfig` does nothing when the rule is already gone, and
     * `saveSlotAdmissionRuleConfig` turns a missing id into a message the dialog can show - and all
     * of them got an `INTERNAL_ERROR` with no message instead (#1793).
     */
    fun findAdmissionRuleConfigById(id: String): SlotAdmissionRuleConfig? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                    FROM ENV_SLOT_ADMISSION_RULE_CONFIGS
                    WHERE ID = :id
            """,
            mapOf(
                "id" to id,
            )
        ) { rs, _ ->
            toSlotAdmissionRuleConfig(
                rs,
                slot = slotRepository.getSlotById(rs.getString("slot_id"))
            )
        }.firstOrNull()

    fun getAdmissionRuleConfigById(slot: Slot, id: String): SlotAdmissionRuleConfig =
        namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT *
                    FROM ENV_SLOT_ADMISSION_RULE_CONFIGS
                    WHERE SLOT_ID = :slotId
                    AND ID = :id
            """,
            mapOf(
                "slotId" to slot.id,
                "id" to id,
            )
        ) { rs, _ ->
            toSlotAdmissionRuleConfig(rs, slot)
        } ?: throw SlotAdmissionRuleConfigIdNotFound(id)

}