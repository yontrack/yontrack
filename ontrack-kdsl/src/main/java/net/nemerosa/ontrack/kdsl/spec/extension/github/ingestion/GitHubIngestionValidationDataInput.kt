package net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion

import tools.jackson.databind.JsonNode

data class GitHubIngestionValidationDataInput(
    val type: String,
    val data: JsonNode,
)