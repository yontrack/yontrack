package net.nemerosa.ontrack.extension.config.ci.model

import tools.jackson.databind.JsonNode

interface CIExtensionsConfig {
    val extensions: Map<String, JsonNode>
}