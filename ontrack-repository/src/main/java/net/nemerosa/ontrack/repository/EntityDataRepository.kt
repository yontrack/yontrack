package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType

/**
 * Store for data associated with project entities.
 */
interface EntityDataRepository {

    /**
     * Store
     */
    fun store(entity: ProjectEntity, key: String, value: String)

    /**
     * Retrieve
     */
    fun retrieve(entity: ProjectEntity, key: String): String?

    /**
     * Store JSON
     */
    fun storeJson(entity: ProjectEntity, key: String, value: JsonNode)

    /**
     * Retrieve JSON
     */
    fun retrieveJson(entity: ProjectEntity, key: String): JsonNode?

    /**
     * Gets the number of entries for a given key
     */
    fun countByKey(key: String): Int

    /**
     * Checks if the given [entity] has a value associated with the [key]
     */
    fun hasEntityValue(entity: ProjectEntity, key: String): Boolean

    /**
     * Looks for a linked entry using a JSON value
     */
    fun findEntityByValue(type: ProjectEntityType, key: String, value: JsonNode): ProjectEntityID?

    /**
     * Delete
     */
    fun delete(entity: ProjectEntity, key: String)

    /**
     * Looking for entities using JSON query.
     */
    fun findEntities(
        type: ProjectEntityType,
        key: String,
        jsonQuery: String = "",
        jsonQueryParameters: Map<String, Any> = emptyMap(),
    ): List<ProjectEntityID>

}
