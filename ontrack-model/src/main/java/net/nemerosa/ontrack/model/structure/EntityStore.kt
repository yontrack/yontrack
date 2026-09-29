package net.nemerosa.ontrack.model.structure

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

    /**
     * Number of records in a store, for all entities
     */
    fun getCountByStoreForAllEntities(store: String): Int

    /**
     * Entities of the given [type] having at least one record in the [store] which matches the
     * [filter], the most recently stored first.
     */
    fun findEntities(
        type: ProjectEntityType,
        store: String,
        filter: EntityStoreFilter = EntityStoreFilter(),
    ): List<ProjectEntityID>

    companion object {
        /**
         * Name of the record in a store which holds only one record per entity.
         *
         * The data of the former `EntityDataService` was moved into such stores (#1925).
         */
        const val DEFAULT_NAME = "default"
    }

}