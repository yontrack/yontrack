package net.nemerosa.ontrack.extension.environments

import tools.jackson.databind.JsonNode

data class SlotAdmissionRuleInput(
    val config: SlotAdmissionRuleConfig,
    val data: JsonNode?,
)
