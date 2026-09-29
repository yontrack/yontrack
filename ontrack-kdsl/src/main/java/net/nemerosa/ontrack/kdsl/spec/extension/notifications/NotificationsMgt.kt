package net.nemerosa.ontrack.kdsl.spec.extension.notifications

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.NotificationRecordsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SubscribeToEntityEventsMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SubscriptionsByEntityQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ProjectEntityType
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.ProjectEntity

/**
 * Interface for the management of notifications in Ontrack.
 */
class NotificationsMgt(connector: Connector) : Connected(connector) {

    /**
     * Subscribes for notifications.
     *
     * @param name Unique name of the subscription in its scope
     * @param channel Channel to send the notifications to
     * @param channelConfig Configuration of the channel
     * @param keywords Space-separated list of keywords to filter the events
     * @param events Events to listen to
     * @param projectEntity Entity to listen to (null for global subscriptions)
     * @param contentTemplate Custom template for the event
     */
    fun subscribe(
        name: String,
        channel: String,
        channelConfig: Any,
        keywords: String? = null,
        events: List<String>,
        projectEntity: ProjectEntity?,
        contentTemplate: String? = null,
    ) {
        if (projectEntity != null) {
            graphqlConnector.mutate(
                SubscribeToEntityEventsMutation(
                    name = name,
                    entityType = projectEntity.type,
                    entityId = projectEntity.id.toInt(),
                    channel = channel,
                    channelConfig = channelConfig.asJson(),
                    keywords = Optional.presentIfNotNull(keywords),
                    events = events,
                    contentTemplate = Optional.presentIfNotNull(contentTemplate),
                )
            ) {
                it?.subscribeToEvents?.payloadUserErrors?.convert()
            }
        } else {
            TODO("Global subscriptions not supported yet")
        }
    }

    fun subscriptions(projectEntity: ProjectEntity, offset: Int = 0, size: Int = 10): List<Subscription> =
        graphqlConnector.query(
            SubscriptionsByEntityQuery(
                entityType = projectEntity.type,
                entityId = projectEntity.id.toInt(),
                offset = Optional.presentIfNotNull(offset),
                size = Optional.presentIfNotNull(size)
            )
        )?.eventSubscriptions?.pageItems?.map {
            Subscription(
                name = it.name,
                channel = it.channel,
                channelConfig = it.channelConfig,
                events = it.events,
                keywords = it.keywords,
                disabled = it.disabled ?: false,
                contentTemplate = it.contentTemplate,
            )
        } ?: emptyList()

    /**
     * Access to the in-memory notification channel.
     */
    val inMemory: InMemoryMgt by lazy {
        InMemoryMgt(connector)
    }

    /**
     * Gets the last notification record for a given channel
     */
    fun notificationRecords(
        channel: String?,
        projectEntityType: ProjectEntityType? = null,
        projectEntityId: Int? = null,
    ): List<NotificationRecord> {
        return graphqlConnector.query(
            NotificationRecordsQuery(
                Optional.presentIfNotNull(channel),
                Optional.presentIfNotNull(projectEntityType),
                Optional.presentIfNotNull(projectEntityId),
            )
        )?.notificationRecords?.pageItems
            ?.map {
                NotificationRecord(
                    id = it.id,
                    source = it.source?.let { source ->
                        NotificationSourceData(
                            id = source.id,
                            data = source.data,
                        )
                    },
                    timestamp = it.timestamp,
                    channel = it.channel,
                    channelConfig = it.channelConfig,
                    event = it.event,
                    result = it.result.let { result ->
                        NotificationRecordResult(
                            type = result.type.name,
                            message = result.message,
                            output = result.output,
                        )
                    }
                )
            } ?: emptyList()
    }


}