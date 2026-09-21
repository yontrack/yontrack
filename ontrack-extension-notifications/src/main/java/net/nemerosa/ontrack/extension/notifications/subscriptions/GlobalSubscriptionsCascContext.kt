package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.notifications.casc.NotificationsSubCascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import org.springframework.stereotype.Component

@Component
class GlobalSubscriptionsCascContext(
    private val eventSubscriptionService: EventSubscriptionService,
) : AbstractCascContext(), NotificationsSubCascContext {

    override val field: String = "global-subscriptions"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of global subscriptions",
            items = jsonTypeBuilder.toType(SubscriptionsCascContextData::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<SubscriptionsCascContextData>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${SubscriptionsCascContextData::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }
        items.forEach { subscription ->
            eventSubscriptionService.subscribe(
                EventSubscription(
                    name = subscription.name ?: subscription.computeName(),
                    projectEntity = null,
                    events = subscription.events.toSet(),
                    keywords = subscription.keywords,
                    channel = subscription.channel,
                    channelConfig = subscription.channelConfig,
                    disabled = subscription.disabled ?: false,
                    origin = EventSubscriptionOrigins.CASC,
                    contentTemplate = subscription.contentTemplate?.trimIndent(),
                )
            )
        }
    }

    override fun render(): JsonNode =
        eventSubscriptionService.filterSubscriptions(
            EventSubscriptionFilter(
                size = 1000,
            )
        ).pageItems.map {
            SubscriptionsCascContextData(
                name = it.name,
                events = it.events.toList(),
                keywords = it.keywords,
                channel = it.channel,
                channelConfig = it.channelConfig,
                disabled = it.disabled,
                contentTemplate = it.contentTemplate,
            )
        }.asJson()

}