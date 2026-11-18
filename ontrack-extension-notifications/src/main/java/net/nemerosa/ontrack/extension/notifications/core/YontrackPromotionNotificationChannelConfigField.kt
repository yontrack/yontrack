package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.SelfDocumented
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.PromotionRunFieldValue
import tools.jackson.databind.node.StringNode

/**
 * Field to set on the promotion run
 */
@SelfDocumented
data class YontrackPromotionNotificationChannelConfigField(
    @APIDescription("Name of the field")
    @APILabel("Name")
    val name: String,
    @APIDescription("[template] Value of the field")
    @APILabel("Value")
    val value: String,
)

/**
 * Renders the values of the fields from the event.
 */
internal fun List<YontrackPromotionNotificationChannelConfigField>.renderFieldValues(
    eventTemplatingService: EventTemplatingService,
    event: Event,
    context: Map<String, Any>,
): List<PromotionRunFieldValue> =
    map { field ->
        PromotionRunFieldValue(
            name = field.name,
            value = StringNode(
                eventTemplatingService.render(
                    template = field.value,
                    event = event,
                    context = context,
                    renderer = PlainEventRenderer.INSTANCE,
                )
            ),
        )
    }
