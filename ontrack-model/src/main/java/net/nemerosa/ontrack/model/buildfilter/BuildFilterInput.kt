package net.nemerosa.ontrack.model.buildfilter

import tools.jackson.databind.JsonNode
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class BuildFilterInput(
    @get:Size(min = 1, message = "The build filter name is required.")
    @get:NotNull(message = "The build filter name is required.")
    val name: String?,
    @get:NotNull(message = "The build filter type is required.")
    val type: String?,
    @get:NotNull(message = "The build filter data is required.")
    val data: JsonNode?,
    val isShared: Boolean,
)
