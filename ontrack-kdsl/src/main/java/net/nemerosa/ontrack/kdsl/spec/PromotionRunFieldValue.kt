package net.nemerosa.ontrack.kdsl.spec

import tools.jackson.databind.JsonNode

data class PromotionRunFieldValue(
    val name: String,
    val value: JsonNode?,
)
