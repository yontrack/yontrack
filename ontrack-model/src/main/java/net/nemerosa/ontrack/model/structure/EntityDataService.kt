package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode

/**
 * This service allows to store and retrieve arbitrary data with some
 * [project entities][net.nemerosa.ontrack.model.structure.ProjectEntity].
 */
@Deprecated("Will be removed in V5. Use the simpler EntityStore service.")
interface EntityDataService {

    /**
     * Stores boolean data
     */
    fun store(entity: ProjectEntity, key: String, value: Boolean)

    /**
     * Stores integer data
     */
    fun store(entity: ProjectEntity, key: String, value: Int)

    /**
     * Stores string data
     */
    fun store(entity: ProjectEntity, key: String, value: String)

    /**
     * Stores arbitrary data as JSON
     */
    fun store(entity: ProjectEntity, key: String, value: Any)

    /**
     * Retrieves data as boolean
     */
    fun retrieveBoolean(entity: ProjectEntity, key: String): Boolean?

    /**
     * Retrieves data as integer
     */
    fun retrieveInteger(entity: ProjectEntity, key: String): Int?

    /**
     * Retrieves arbitrary data as string
     */
    fun retrieve(entity: ProjectEntity, key: String): String?

    /**
     * Retrieves arbitrary data as JSON
     */
    fun retrieveJson(entity: ProjectEntity, key: String): JsonNode?

    /**
     * Retrieves arbitrary data as JSON
     */
    fun <T> retrieve(entity: ProjectEntity, key: String, type: Class<T>): T?

    /**
     * Gets the number of entries for a given key
     */
    fun countByKey(key: String): Int

    /**
     * Checks if the given [entity] has a value associated with the [key]
     */
    fun hasEntityValue(entity: ProjectEntity, key: String): Boolean

    /**
     * Looks for a linked entry using a text value
     */
    fun findEntityByValue(type: ProjectEntityType, key: String, value: JsonNode): ProjectEntityID?

    /**
     * Looking for entities using JSON query.
     */
    fun findEntities(
        type: ProjectEntityType,
        key: String,
        jsonQuery: String = "",
        jsonQueryParameters: Map<String, Any> = emptyMap(),
    ): List<ProjectEntityID>

    /**
     * Deletes data
     *
     * @param entity Entity to delete data from
     * @param key    Key to delete
     */
    fun delete(entity: ProjectEntity, key: String)

    /**
     * Loads some data, processes it and saves it back
     */
    fun <T> withData(entity: ProjectEntity, key: String, type: Class<T>, processFn: (T) -> T)

}
