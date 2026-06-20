package net.nemerosa.ontrack.kdsl.spec

import com.fasterxml.jackson.databind.JsonNode

data class PromotionRunFieldValue(
    val name: String,
    val value: JsonNode?,
)
