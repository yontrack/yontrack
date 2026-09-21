package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.extension.notifications.channels.getChannel
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Component

@Component
class GlobalSubscriptionStore(
    private val storageService: StorageService,
    private val notificationChannelRegistry: NotificationChannelRegistry,
) {
    fun save(record: SubscriptionRecord) {
        storageService.store(
            GLOBAL_STORE,
            record.name,
            record,
        )
    }

    fun find(name: String): SubscriptionRecord? =
        storageService.find(GLOBAL_STORE, name, SubscriptionRecord::class)

    fun delete(name: String) {
        storageService.delete(GLOBAL_STORE, name)
    }

    fun filter(filter: EventSubscriptionFilter, offset: Int, size: Int): Pair<Int, List<SubscriptionRecord>> {
        // Query contexts & criteria
        val contextList = mutableListOf<String>()
        val jsonFilters = mutableListOf<String>()
        val jsonCriteria = mutableMapOf<String, String>()

        // Filter: name
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
                jsonFilters += """data::jsonb->'channelConfig' @> '$criteria'"""
            }
        }
        // Filter: origin
        if (!filter.origin.isNullOrBlank()) {
            jsonFilters += """data::jsonb->>'origin' = :origin"""
            jsonCriteria["origin"] = filter.origin
        }
        // Filter: event type
        if (!filter.eventType.isNullOrBlank()) {
            contextList += "left join jsonb_array_elements_text(data::jsonb->'events') as events on true"
            jsonFilters += """events = :eventType"""
            jsonCriteria["eventType"] = filter.eventType
        }

        // Final context & criteria
        val context = contextList.joinToString(" ")
        val jsonFilter = jsonFilters.joinToString(" AND ") { "( $it )" }

        // Counts the total
        val total = storageService.count(
            store = GLOBAL_STORE,
            context = context,
            query = jsonFilter,
            queryVariables = jsonCriteria,
        )
        // Gets the items
        val items = storageService.filterRecords(
            store = GLOBAL_STORE,
            type = SubscriptionRecord::class,
            context = context,
            query = jsonFilter,
            queryVariables = jsonCriteria,
            offset = offset,
            size = size,
        ).values.sortedBy { it.name }
        // OK
        return total to items
    }

    fun deleteAll() {
        storageService.deleteWithFilter(GLOBAL_STORE)
    }

    fun forEachRecord(
        context: String = "",
        query: String? = null,
        queryVariables: Map<String, *>? = null,
        code: (SubscriptionRecord) -> Unit,
    ) {
        storageService.forEach(
            store = GLOBAL_STORE,
            type = SubscriptionRecord::class,
            context = context,
            query = query,
            queryVariables = queryVariables,
        ) { _, record ->
            code(record)
        }
    }

    /**
     * Migration of all existing subscriptions to have a name
     */
    internal fun migrateSubscriptionNames() {
        // Getting the whole store in memory
        val store = storageService.getData(GLOBAL_STORE)
        // Deleting the whole store
        storageService.clear(GLOBAL_STORE)
        // Converting each entry
        val entries = store.values.associate { record ->
            val name = record.path("name").asText()
            if (name.isNullOrBlank()) {
                val generatedName = EventSubscription.computeName(
                    events = record.path("events").values().map { it.asText() },
                    keywords = record.getTextField("keywords"),
                    channel = record.getRequiredTextField("channel"),
                    channelConfig = record.path("channelConfig"),
                    contentTemplate = record.getTextField("contentTemplate"),
                )
                (record as ObjectNode).put("name", generatedName)
                generatedName to record
            } else {
                // Name already filled in, skipping
                name to record
            }
        }
        // Saving the entries back
        entries.forEach { (name, record) ->
            storageService.store(GLOBAL_STORE, name, record)
        }
    }

    companion object {
        private val GLOBAL_STORE = EventSubscription::class.java.name
    }

}