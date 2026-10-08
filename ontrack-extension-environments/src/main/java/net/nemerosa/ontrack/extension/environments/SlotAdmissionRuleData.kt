package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.model.structure.SignatureActor
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Data given to an admission rule for a pipeline.
 *
 * @property user Who gave the data
 * @property actor The agent behind the data, null for a person
 */
data class SlotAdmissionRuleData(
    val user: String,
    val timestamp: LocalDateTime,
    val data: JsonNode,
    val actor: SignatureActor? = null,
)
