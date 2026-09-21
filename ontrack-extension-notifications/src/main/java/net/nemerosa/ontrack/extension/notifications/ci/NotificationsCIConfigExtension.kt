package net.nemerosa.ontrack.extension.notifications.ci

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.notifications.NotificationsExtensionFeature
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.extension.notifications.channels.getChannel
import net.nemerosa.ontrack.extension.notifications.channels.getConfigIfOk
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscription
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionOrigins
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class NotificationsCIConfigExtension(
    notificationsExtensionFeature: NotificationsExtensionFeature,
    private val eventSubscriptionService: EventSubscriptionService,
    private val structureService: StructureService,
    private val notificationChannelRegistry: NotificationChannelRegistry,
) : AbstractExtension(notificationsExtensionFeature), CIConfigExtension<NotificationsCIConfig> {

    override val id: String = "notificationsConfig"

    override val projectEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.BRANCH)

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(NotificationsCIConfig::class)

    override fun parseData(data: JsonNode): NotificationsCIConfig = data.parse()

    override fun mergeConfig(
        defaults: NotificationsCIConfig,
        custom: JsonNode
    ): NotificationsCIConfig {
        val customConfig = custom.parse<NotificationsCIConfig>()
        val result = defaults.notifications.toMutableList()

        syncForward(
            from = customConfig.notifications,
            to = defaults.notifications,
        ) {
            equality { i, j -> i.name == j.name }

            onDeletion { _ -> }

            onCreation { e -> result.add(e) }

            onModification { e, existing ->
                result.removeIf { it.name == existing.name }
                result.add(
                    mergeItem(existing, e)
                )
            }
        }

        return NotificationsCIConfig(notifications = result)
    }

    private fun mergeItem(
        existing: NotificationsCIConfigItem,
        e: NotificationsCIConfigItem
    ): NotificationsCIConfigItem {

        var channel = existing.channel
        var channelConfig = existing.channelConfig
        if (channel != null && (e.channel == null || e.channel == channel)) {
            // Same channel, merging the config
            val notificationChannel = notificationChannelRegistry.getChannel(channel)
            channelConfig = mergeConfig(notificationChannel, channelConfig, e.channelConfig)

        } else if (channel == null) {
            // No initial channel
            channel = e.channel
            channelConfig = e.channelConfig
        }

        return NotificationsCIConfigItem(
            name = existing.name,
            channel = channel,
            channelConfig = channelConfig,
            events = e.events.ifEmpty { existing.events },
            keywords = e.keywords ?: existing.keywords,
            promotion = e.promotion ?: existing.promotion,
            contentTemplate = e.contentTemplate ?: existing.contentTemplate,
        )
    }

    private fun <C> mergeConfig(
        notificationChannel: NotificationChannel<C, *>,
        configA: JsonNode,
        configB: JsonNode
    ): JsonNode {
        val parsedConfigA = notificationChannel.validate(configA)
            .getConfigIfOk()
        val finalConfig = notificationChannel.mergeConfig(parsedConfigA, configB)
        return finalConfig.asJson()
    }

    override fun configure(
        entity: ProjectEntity,
        data: NotificationsCIConfig
    ) {
        data.notifications.forEach { notification ->
            configureNotification(
                branch = entity as Branch,
                notification = notification,
            )
        }
    }

    private fun configureNotification(
        branch: Branch,
        notification: NotificationsCIConfigItem
    ) {
        // Target for the subscription
        val target = when {
            !notification.promotion.isNullOrBlank() -> getPromotion(branch, notification.promotion)
            else -> branch
        }

        val events = notification.events.toSet()
            .takeIf { it.isNotEmpty() }
            ?: throw NotificationsCIConfigException("Events are required")

        // Subscription
        eventSubscriptionService.subscribe(
            EventSubscription(
                projectEntity = target,
                name = notification.name,
                events = events,
                keywords = notification.keywords,
                channel = notification.channel ?: throw NotificationsCIConfigException("Channel is required"),
                channelConfig = notification.channelConfig,
                disabled = false,
                origin = EventSubscriptionOrigins.CI,
                contentTemplate = notification.contentTemplate,
            )
        )
    }

    private fun getPromotion(
        branch: Branch,
        promotion: String
    ): ProjectEntity =
        structureService.findPromotionLevelByName(
            branch.project.name,
            branch.name,
            promotion
        ).getOrNull()
            ?: throw NotificationsCIConfigPromotionNotFoundException(promotion)
}