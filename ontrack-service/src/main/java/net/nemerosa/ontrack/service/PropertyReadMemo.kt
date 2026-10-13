package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.support.ReadMemo
import net.nemerosa.ontrack.repository.PropertyRepository
import net.nemerosa.ontrack.repository.TProperty
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * The properties, read through the [ReadMemo] of the unit of work (#2081, ADR 0021): never cached
 * across units, so that a property written through another replica is read by the next request.
 *
 * In a unit of work:
 *
 * - the stored properties are loaded **by entity**, all the properties of an entity in one query,
 *   whatever the property first asked for;
 * - the entities [prefetched][prefetch] together, like the items of a list, are loaded together,
 *   in one query, on the first read of one of them;
 * - the decoded values are held too, by type and entity, so that a property is decoded once;
 * - a property being [written] is forgotten, so that the unit reads it again - and once more when
 *   its transaction is over, so that a rollback does not leave the value it wrote.
 *
 * Outside of a unit of work, as in a job, every read goes to the database.
 */
internal class PropertyReadMemo(
    private val propertyRepository: PropertyRepository,
) {

    /**
     * Stored property of the given type for an entity, if any.
     */
    fun stored(typeName: String, entityType: ProjectEntityType, entityId: ID): TProperty? {
        val memo = ReadMemo.current()
            ?: return propertyRepository.loadProperty(typeName, entityType, entityId)
        return entityProperties(memo, EntityKey(entityType, entityId.value))[typeName]
    }

    /**
     * All the stored properties of an entity, indexed by type name.
     */
    fun stored(entityType: ProjectEntityType, entityId: ID): Map<String, TProperty> {
        val memo = ReadMemo.current()
            ?: return propertyRepository.loadProperties(entityType, listOf(entityId)).associateBy { it.propertyTypeName }
        return entityProperties(memo, EntityKey(entityType, entityId.value))
    }

    /**
     * Decoded value of the property of the given type for an entity: from the memo, or from the
     * [decoder], whose outcome - a value or a failure - is held by the memo.
     */
    fun decoded(typeName: String, entityType: ProjectEntityType, entityId: ID, decoder: () -> Any?): Result<Any?> =
        ReadMemo.read(DECODED, PropertyKey(typeName, EntityKey(entityType, entityId.value))) {
            try {
                Result.success(decoder())
            } catch (any: Exception) {
                Result.failure(any)
            }
        }

    /**
     * The properties of these entities are loaded together, on the first read of one of them.
     */
    fun prefetch(entities: Collection<ProjectEntity>) {
        val memo = ReadMemo.current() ?: return
        entities
            .filter { ID.isDefined(it.id) }
            .groupBy { it.projectEntityType }
            .forEach { (entityType, list) ->
                val keys = list
                    .map { EntityKey(entityType, it.id.value) }
                    .filter { !memo.contains(STORED, it) }
                    .toSet()
                // A single entity loads itself on its first read
                if (keys.size > 1) {
                    val group = Group(keys)
                    keys.forEach { memo.put(GROUPS, it, group) }
                }
            }
    }

    /**
     * The property of the given type has been written or deleted for an entity.
     */
    fun written(typeName: String, entityType: ProjectEntityType, entityId: ID) {
        val key = EntityKey(entityType, entityId.value)
        val propertyKey = PropertyKey(typeName, key)
        evict(key, propertyKey)
        if (ReadMemo.current() != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    evict(key, propertyKey)
                }
            })
        }
    }

    private fun evict(key: EntityKey, propertyKey: PropertyKey) {
        ReadMemo.evict(STORED, key)
        ReadMemo.evict(DECODED, propertyKey)
    }

    /**
     * Properties of the entity, loaded with those of its group if it belongs to one.
     */
    private fun entityProperties(memo: ReadMemo, key: EntityKey): Map<String, TProperty> {
        if (!memo.contains(STORED, key)) {
            val keys = if (memo.contains(GROUPS, key)) {
                val group = memo.getOrLoad(GROUPS, key) { Group(setOf(key)) }
                // Some entities of the group may have been loaded since, as part of another group
                group.keys.filter { it == key || !memo.contains(STORED, it) }
            } else {
                listOf(key)
            }
            load(memo, key.entityType, keys)
        }
        return memo.getOrLoad(STORED, key) { emptyMap() }
    }

    private fun load(memo: ReadMemo, entityType: ProjectEntityType, keys: Collection<EntityKey>) {
        val properties = propertyRepository.loadProperties(entityType, keys.map { ID.of(it.id) })
            .groupBy { it.entityId.value }
        keys.forEach { key ->
            memo.put(STORED, key, properties[key.id]?.associateBy { it.propertyTypeName } ?: emptyMap())
            memo.remove(GROUPS, key)
        }
    }

    private data class EntityKey(val entityType: ProjectEntityType, val id: Int)

    private data class PropertyKey(val typeName: String, val entity: EntityKey)

    /**
     * Entities whose properties are loaded together
     */
    private class Group(val keys: Set<EntityKey>)

    companion object {
        /**
         * Stored properties of an entity, by type name
         */
        private val STORED = ReadMemo.Region<EntityKey, Map<String, TProperty>>("properties.stored")

        /**
         * Group of an entity whose properties are not loaded yet
         */
        private val GROUPS = ReadMemo.Region<EntityKey, Group>("properties.groups")

        /**
         * Decoded value of a property, or the failure to decode it
         */
        private val DECODED = ReadMemo.Region<PropertyKey, Result<Any?>>("properties.decoded")
    }
}
