package net.nemerosa.ontrack.extension.environments

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

data class SlotAdmissionRuleData(
    val user: String,
    val timestamp: LocalDateTime,
    val data: JsonNode,
)
