package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.extension.notifications.channels.getChannel
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class EntitySubscriptionStore(
    private val entityStore: EntityStore,
    private val notificationChannelRegistry: NotificationChannelRegistry,
) {

    fun save(entity: ProjectEntity, record: SubscriptionRecord) {
        entityStore.store(entity, ENTITY_STORE, record.name, record)
    }

    fun findByName(entity: ProjectEntity, name: String): SubscriptionRecord? =
        entityStore.findByName<SubscriptionRecord>(entity, ENTITY_STORE, name)

    fun deleteByName(projectEntity: ProjectEntity, name: String) {
        entityStore.deleteByName(projectEntity, ENTITY_STORE, name)
    }

    fun deleteAll(projectEntity: ProjectEntity) {
        entityStore.deleteByStore(projectEntity, ENTITY_STORE)
    }

    fun deleteByOrigin(projectEntity: ProjectEntity, origin: String) {
        entityStore.deleteByFilter(
            entity = projectEntity,
            store = ENTITY_STORE,
            filter = EntityStoreFilter(
                jsonFilter = """data::jsonb->>'origin' = :origin""",
                jsonFilterCriterias = mapOf("origin" to origin),
            )
        )
    }

    fun findByFilter(
        entity: ProjectEntity,
        offset: Int,
        size: Int,
        filter: EventSubscriptionFilter
    ): Pair<Int, List<SubscriptionRecord>> {
        // All JSON context
        var jsonContextEvents = false
        val jsonFilters = mutableListOf<String>()
        val jsonCriteria = mutableMapOf<String, String>()
        // Filter: channel
        if (!filter.name.isNullOrBlank()) {
            jsonFilters += """data::jsonb->>'name' = :name"""
            jsonCriteria["name"] = filter.name
        }
        // Filter: channel
        if (!filter.channel.isNullOrBlank()) {
            jsonFilters += """data::jsonb->>'channel' = :channel"""
            jsonCriteria["channel"] = filter.channel
            // Filter: channel config
            if (!filter.channelConfig.isNullOrBlank()) {
                val channel = notificationChannelRegistry.getChannel(filter.channel)
                val criteria = channel.toSearchCriteria(filter.channelConfig).format()
                jsonFilters += """data::jsonb->'channelConfig' @> '$criteria'::jsonb"""
            }
        }
        // Filter: origin
        if (!filter.origin.isNullOrBlank()) {
            jsonFilters += """data::jsonb->>'origin' = :origin"""
            jsonCriteria["origin"] = filter.origin
        }
        // Filter: event type
        if (!filter.eventType.isNullOrBlank()) {
            jsonContextEvents = true
            jsonFilters += """events = :eventType"""
            jsonCriteria["eventType"] = filter.eventType
        }
        // Json context
        val jsonContextList = mutableListOf<String>()
        if (jsonContextEvents) {
            jsonContextList += "left join jsonb_array_elements_text(data::jsonb->'events') as events on true"
        }
        val jsonContext = if (jsonContextList.isNotEmpty()) {
            jsonContextList.joinToString(" ")
        } else {
            null
        }
        // Json filter
        val jsonFilter = EntityStoreFilter(
            jsonContext = jsonContext,
            jsonFilter = jsonFilters.joinToString(" AND ") { "( $it )" },
            jsonFilterCriterias = jsonCriteria,
        )
        // Total count for THIS entity
        val count = entityStore.getCountByFilter(entity, ENTITY_STORE, jsonFilter)
        // Loading & converting the records
        val items = entityStore.getByFilter<SubscriptionRecord>(
            entity = entity,
            store = ENTITY_STORE,
            offset = offset,
            size = size,
            jsonFilter = jsonFilter
        )
        // OK
        return count to items
    }

    fun forEachByEvent(projectEntity: ProjectEntity, event: Event, code: (SubscriptionRecord) -> Unit) {
        val filter = EntityStoreFilter(
            jsonContext = "left join jsonb_array_elements_text(data::jsonb->'events') as events on true",
            jsonFilter = "events = :event",
            jsonFilterCriterias = mapOf(
                "event" to event.eventType.id
            )
        )
        entityStore.forEachByFilter<SubscriptionRecord>(
            entity = projectEntity,
            store = ENTITY_STORE,
            filter = filter,
            code = code,
        )
    }

    /**
     * Moves all records from entity_data_store to entity_store
     * and generates names.
     */
    fun migrateSubscriptionNames() {
        entityStore.migrateFromEntityDataStore(
            category = ENTITY_STORE,
        ) { _, data ->
            val generatedName = EventSubscription.computeName(
                events = data.path("events").values().map { it.asText() },
                keywords = data.getTextField("keywords"),
                channel = data.getRequiredTextField("channel"),
                channelConfig = data.path("channelConfig"),
                contentTemplate = data.getTextField("contentTemplate"),
            )
            (data as ObjectNode).put("name", generatedName)
            generatedName to data
        }
    }

    fun clearAll() {
        entityStore.deleteByStoreForAllEntities(ENTITY_STORE)
    }

    companion object {
        private val ENTITY_STORE = EventSubscription::class.java.name
    }

}