package net.nemerosa.ontrack.extension.environments

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.service.SlotAdmissionRuleConfigNameFormatException
import java.util.*

/**
 * Configuration of an admission rule for a slot.
 */
data class SlotAdmissionRuleConfig(
    val id: String = UUID.randomUUID().toString(),
    val slot: Slot,
    val description: String?,
    val ruleId: String,
    val ruleConfig: JsonNode,
    val name: String = ruleId,
) {
    fun withDescription(description: String?) = SlotAdmissionRuleConfig(
        id = id,
        slot = slot,
        name = name,
        description = description,
        ruleId = ruleId,
        ruleConfig = ruleConfig
    )

    fun withRuleConfig(ruleConfig: JsonNode) = SlotAdmissionRuleConfig(
        id = id,
        slot = slot,
        name = name,
        description = description,
        ruleId = ruleId,
        ruleConfig = ruleConfig
    )

    fun checkName() {
        checkRuleName(name)
    }

    companion object {

        const val PATTERN = "^[a-zA-Z][a-zA-Z0-9-]*\$"
        private val regex = PATTERN.toRegex()

        fun checkRuleName(name: String) {
            if (!regex.matches(name)) {
                throw SlotAdmissionRuleConfigNameFormatException(name)
            }
        }
    }
}
