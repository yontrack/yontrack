package net.nemerosa.ontrack.kdsl.spec.support

import tools.jackson.databind.JsonNode

data class ServiceConfiguration(
    val id: String,
    val data: JsonNode?,
)