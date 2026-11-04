package net.nemerosa.ontrack.extension.config.ci.model

import tools.jackson.databind.JsonNode

interface CIPropertiesConfig {
    /**
     * Configuration of the properties, per alias or class name. A null value deletes the property.
     */
    val properties: Map<String, JsonNode?>
}