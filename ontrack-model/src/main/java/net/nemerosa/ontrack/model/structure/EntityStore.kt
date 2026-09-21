package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import kotlin.reflect.KClass

/**
 * Service used to store values at entity level.
 */
interface EntityStore {

    /**
     * Stores some arbitrary object for an entity
     */
    fun store(
        entity: ProjectEntity,
        store: String,
        name: String,
        data: Any,
    )

    /**
     * Finds data using its name
     */
    fun <T : Any> findByName(entity: ProjectEntity, store: String, name: String, type: KClass<T>): T?

    /**
     * Deletes some data using its name
     */
    fun deleteByName(entity: ProjectEntity, store: String, name: String)

    /**
     * Deletes some data for the whole store
     */
    fun deleteByStore(entity: ProjectEntity, store: String)

    /**
     * Deleting some data using a filter
     */
    fun deleteByFilter(entity: ProjectEntity, store: String, filter: EntityStoreFilter)


    fun getCountByFilter(entity: ProjectEntity, store: String, filter: EntityStoreFilter): Int

    fun <T : Any> getByFilter(
        entity: ProjectEntity,
        store: String,
        offset: Int = 0,
        size: Int = 10,
        filter: EntityStoreFilter,
        type: KClass<T>
    ): List<T>

    fun <T : Any> forEachByFilter(
        entity: ProjectEntity,
        store: String,
        type: KClass<T>,
        filter: EntityStoreFilter,
        code: (T) -> Unit
    )

    fun deleteByStoreForAllEntities(store: String)

    fun migrateFromEntityDataStore(category: String, migration: (name: String, data: JsonNode) -> Pair<String, JsonNode>)

}