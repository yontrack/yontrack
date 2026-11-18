package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.DocumentationList
import net.nemerosa.ontrack.model.json.DurationDeserializer
import net.nemerosa.ontrack.model.json.DurationSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import java.time.Duration

data class YontrackDependenciesPromotionNotificationChannelConfig(
    @APIDescription("Regular expression which must match the whole name of the project of the linked build. If blank or not provided, all projects are selected.")
    val project: String? = null,
    @APIDescription("Regular expression which must match the whole qualifier of the link. If not provided, all links are selected. An empty value selects the unqualified links only.")
    val qualifier: String? = null,
    @APIDescription("Name of the promotion level to use, the same for all the linked builds.")
    val promotion: String,
    @APIDescription("Fields to set on the promotion runs. Their values are rendered once from the event and set on every run.")
    @DocumentationList
    val fields: List<YontrackPromotionNotificationChannelConfigField> = emptyList(),
    @APIDescription("Waiting for the notifications of the promotion runs created by this notification to be completed")
    val waitForPromotion: Boolean = false,
    @APIDescription("Timeout when waiting for the notifications of the created promotion runs to be completed")
    @JsonSerialize(using = DurationSerializer::class)
    @JsonDeserialize(using = DurationDeserializer::class)
    val waitForPromotionTimeout: Duration = Duration.ofMinutes(5),
)
