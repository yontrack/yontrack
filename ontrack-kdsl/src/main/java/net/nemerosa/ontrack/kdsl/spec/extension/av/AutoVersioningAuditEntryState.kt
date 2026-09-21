package net.nemerosa.ontrack.kdsl.spec.extension.av

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class AutoVersioningAuditEntryState(
    val state: String,
    val data: JsonNode
)