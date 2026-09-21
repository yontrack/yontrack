package net.nemerosa.ontrack.extension.github.ingestion.extensions.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName

/**
 * Validation data input
 */
@APIName("GitHubIngestionValidationDataInput")
@APIDescription("Validation data input")
data class GitHubIngestionValidationDataInput(
    @APIDescription("FQCN of the validation data type")
    val type: String,
    @APIDescription("Validation data")
    val data: JsonNode,
)