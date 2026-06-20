package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.databind.JsonNode

data class PromotionRunFieldValue(
    val name: String,
    /** Null for optional fields that were not filled in */
    val value: JsonNode?,
)
