package net.nemerosa.ontrack.kdsl.spec.extension.notifications

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotificationRecordResult(
    val type: String,
    val message: String?,
    val output: JsonNode?,
)
