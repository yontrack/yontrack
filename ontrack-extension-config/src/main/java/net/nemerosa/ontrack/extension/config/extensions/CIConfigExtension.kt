package net.nemerosa.ontrack.extension.config.extensions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType

/**
 * Extension which can provide a CI configuration.
 *
 * @param T Data associated with a CI configuration.
 */
interface CIConfigExtension<T> : Extension {

    /**
     * Unique ID for this configuration, referred to in the YAML configuration.
     */
    val id: String

    /**
     * Target entities
     */
    val projectEntityTypes: Set<ProjectEntityType>

    /**
     * Parses the configuration data.
     */
    fun parseData(data: JsonNode): T

    /**
     * Given a CI configuration, configures an entity.
     */
    fun configure(entity: ProjectEntity, data: T)

    /**
     * Merging data together
     */
    fun mergeConfig(defaults: T, custom: JsonNode): T = custom.parse()

    /**
     * JSON type for the schema
     */
    fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType

}