package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode

/**
 * Stores and retrieves arbitrary data using JSON.
 */
interface StorageRepository {

    /**
     * Gets the count of items in a store matching some criteria.
     */
    fun count(
        store: String,
        context: String = "",
        query: String? = null,
        queryVariables: Map<String, *>? = null,
    ): Int

    /**
     * Gets items in a store matching some criteria.
     */
    fun filter(
        store: String,
        offset: Int = 0,
        size: Int = 40,
        context: String = "",
        query: String? = null,
        queryVariables: Map<String, *>? = null,
        orderQuery: String? = null,
    ): List<JsonNode>

    /**
     * Gets items in a store matching some criteria.
     */
    fun filterRecords(
        store: String,
        offset: Int = 0,
        size: Int = 40,
        context: String = "",
        query: String? = null,
        queryVariables: Map<String, *>? = null,
        orderQuery: String? = null,
    ): Map<String, JsonNode>

    /**
     * Loops over items in a store matching some criteria.
     */
    fun forEach(
        store: String,
        context: String = "",
        query: String? = null,
        queryVariables: Map<String, *>? = null,
        orderQuery: String? = null,
        code: (key: String, node: JsonNode) -> Unit
    )

    /**
     * Deletes items in a store matching some criteria.
     *
     * @return Number of items having been deleted
     */
    fun deleteWithFilter(
        store: String,
        query: String? = null,
        queryVariables: Map<String, *>? = null,
    ): Int

    /**
     * Stores some JSON
     *
     * @param store Store (typically an extension class name)
     * @param key   Identifier of data
     * @param node  Data to store
     */
    fun storeJson(store: String, key: String, node: JsonNode)

    /**
     * Increments a counter in one statement (upsert), so that concurrent increments, on any
     * replica, are all counted. The counter is stored as `{"count": <n>}`, and starts at 1.
     *
     * @param store Store (typically an extension class name)
     * @param key   Identifier of the counter
     * @return Count after the increment
     */
    fun increment(store: String, key: String): Int

    /**
     * Retrieves some JSON using a key
     *
     * @param store Store (typically an extension class name)
     * @param key   Identifier of data
     * @return Data or null if not found
     */
    fun retrieveJson(store: String, key: String): JsonNode?

    /**
     * Retrieves some JSON using a key, and locks the entry until the end of the current transaction
     * (`SELECT ... FOR UPDATE`).
     *
     * @param store Store (typically an extension class name)
     * @param key   Identifier of data
     * @return Data or null if not found
     */
    fun retrieveJsonForUpdate(store: String, key: String): JsonNode?

    /**
     * Lists all keys for a store
     *
     * @param store Store (typically an extension class name)
     */
    fun getKeys(store: String): List<String>

    /**
     * Gets all the data for a store
     *
     * @param store Store (typically an extension class name)
     */
    fun getData(store: String): Map<String, JsonNode>

    /**
     * Deletes an entry
     */
    fun delete(store: String, key: String)

    /**
     * Clears the whole store
     */
    fun clear(store: String)

    /**
     * Checks if an entry already exists.
     *
     * @param store Store to check
     * @param key Key to check
     * @return `true` if the entry exists
     */
    fun exists(store: String, key: String): Boolean
}