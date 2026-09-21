package net.nemerosa.ontrack.extension.config.ci.model

import tools.jackson.databind.JsonNode

interface CIPropertiesConfig {
    val properties: Map<String, JsonNode>
}