package net.nemerosa.ontrack.repository.support.store

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.Signature
import java.time.LocalDateTime
import java.util.*
import java.util.function.Consumer

interface EntityDataStore {
    /**
     * Adds a new entry in the store
     *
     * @param entity    Associated entity
     * @param category  Data category
     * @param name      Data name
     * @param signature Data signature
     * @param groupName Data group (optional)
     * @param data      Data
     * @return Data record
     */
    fun add(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: JsonNode,
    ): EntityDataStoreRecord

    /**
     * Overrides an entry in the store
     *
     * @param entity    Associated entity
     * @param category  Data category
     * @param name      Data name
     * @param signature Data signature
     * @param groupName Data group (optional)
     * @param data      Data
     * @return Data record
     */
    fun replaceOrAdd(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: JsonNode,
    ): EntityDataStoreRecord

    /**
     * Gets the audit data for an entry
     *
     * @param id Record ID
     * @return List of audit records, from the most recent to the oldest
     */
    fun getRecordAudit(id: Int): List<EntityDataStoreRecordAudit>

    /**
     * Deletes all entries for a name
     *
     * @param entity   Associated entity
     * @param category Data category
     * @param name     Data name
     */
    fun deleteByName(entity: ProjectEntity, category: String, name: String)

    /**
     * Deletes all entries for a group
     *
     * @param entity    Associated entity
     * @param category  Data category
     * @param groupName Data group name
     */
    fun deleteByGroup(entity: ProjectEntity, category: String, groupName: String)

    /**
     * Deletes all entries for a category before a given time
     *
     * @param category   Data category
     * @param beforeTime To delete before this time
     */
    fun deleteByCategoryBefore(category: String, beforeTime: LocalDateTime)

    /**
     * Retrieves if any, the last data, for an entity, a category and a name
     *
     * @param entity     Entity associated with the data
     * @param category   Data category
     * @param name       Data key in the category
     * @param beforeTime Last record BEFORE this time
     * @return A reference to the JSON data together with the associated time and user.
     */
    fun findLastByCategoryAndName(
        entity: ProjectEntity,
        category: String,
        name: String,
        beforeTime: LocalDateTime?,
    ): EntityDataStoreRecord?

    /**
     * Retrieves if any, the last data, for an entity, a category, a group and a name
     *
     * @param entity    Entity associated with the data
     * @param category  Data category
     * @param groupName Optional name to group some entries together
     * @param name      Data key in the category
     * @return A reference to the JSON data together with the associated time and user.
     */
    fun findLastByCategoryAndGroupAndName(
        entity: ProjectEntity,
        category: String,
        groupName: String,
        name: String,
    ): EntityDataStoreRecord?

    /**
     * Gets a list of entries for a given category, getting only the last of each name
     *
     * @param entity   Entity associated with the data
     * @param category Data category
     * @return List of entries
     */
    fun findLastRecordsByNameInCategory(entity: ProjectEntity, category: String): List<EntityDataStoreRecord>

    /**
     * Adds a new entry in the store
     *
     * @param entity    Associated entity
     * @param category  Data category
     * @param name      Data name
     * @param signature Data signature
     * @param groupName Data group (optional)
     * @param data      Data
     * @return Data record
     */
    fun addObject(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: Any,
    ): EntityDataStoreRecord

    /**
     * Overrides an entry in the store
     *
     * @param entity    Associated entity
     * @param category  Data category
     * @param name      Data name
     * @param signature Data signature
     * @param groupName Data group (optional)
     * @param data      Data
     * @return Data record
     */
    fun replaceOrAddObject(
        entity: ProjectEntity,
        category: String,
        name: String,
        signature: Signature,
        groupName: String?,
        data: Any
    ): EntityDataStoreRecord

    /**
     * Gets a record by ID
     *
     * @param id ID of the record
     * @return Record or empty if not found
     */
    fun getById(entity: ProjectEntity, id: Int): EntityDataStoreRecord?

    /**
     * Gets a list of records for a category and a name
     */
    fun getByCategoryAndName(
        entity: ProjectEntity,
        category: String,
        name: String,
        offset: Int,
        page: Int
    ): List<EntityDataStoreRecord>

    /**
     * Gets a list of records for a category
     */
    fun getByCategory(entity: ProjectEntity, category: String, offset: Int, page: Int): List<EntityDataStoreRecord>

    /**
     * Gets the count of records for a category and a name
     */
    fun getCountByCategoryAndName(entity: ProjectEntity, category: String, name: String): Int

    /**
     * Gets the count of records for a category
     */
    fun getCountByCategory(entity: ProjectEntity, category: String): Int

    /**
     * Deletes EVERYTHING in the store for ALL entities - USE WITH CARE
     */
    fun deleteAll()

    /**
     * Gets a list of records based on a filter
     *
     *
     * Note that the [EntityDataStoreFilter.getEntity] parameter is required.
     */
    fun getByFilter(entityDataStoreFilter: EntityDataStoreFilter): List<EntityDataStoreRecord>

    /**
     * Loops over a list of records based on a filter
     *
     *
     * Note that the [EntityDataStoreFilter.getEntity] parameter is required.
     */
    fun forEachByFilter(entityDataStoreFilter: EntityDataStoreFilter, consumer: (EntityDataStoreRecord) -> Unit)

    /**
     * Gets a count of records based on a filter
     */
    fun getCountByFilter(entityDataStoreFilter: EntityDataStoreFilter): Int

    /**
     * Deletes a list of records based on a filter. Careful, [EntityDataStoreFilter.getCount] and [EntityDataStoreFilter.getOffset]
     * are **ignored** in this call
     */
    fun deleteByFilter(entityDataStoreFilter: EntityDataStoreFilter): Int

    /**
     * Deletes a list of records based on a filter, limiting the deletion to the range
     * specified by [EntityDataStoreFilter.getCount] and [EntityDataStoreFilter.getOffset].
     */
    fun deleteRangeByFilter(entityDataStoreFilter: EntityDataStoreFilter): Int
}
