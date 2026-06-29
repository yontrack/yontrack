package net.nemerosa.ontrack.extension.notifications.core

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.DocumentationList
import net.nemerosa.ontrack.model.json.DurationDeserializer
import net.nemerosa.ontrack.model.json.DurationSerializer
import java.time.Duration

data class YontrackPromotionNotificationChannelConfig(
    @APIDescription("[template] Name of the project to promote. If not provided, looks for the event's project if available.")
    val project: String? = null,
    @APIDescription("[template] Name of the branch to promote. If not provided, looks for the event's branch if available.")
    val branch: String? = null,
    @APIDescription("[template] Name of the build to promote. If not provided, looks for the event's build if available.")
    val build: String? = null,
    @APIDescription("Name of the promotion level to use.")
    val promotion: String,
    @APIDescription("Fields to set on the promotion run")
    @DocumentationList
    val fields: List<YontrackPromotionNotificationChannelConfigField> = emptyList(),
    @APIDescription("Waiting for the promotion level associated notifications to be completed")
    val waitForPromotion: Boolean = false,
    @APIDescription("Timeout when waiting for the promotion level associated notifications to be completed")
    @JsonSerialize(using = DurationSerializer::class)
    @JsonDeserialize(using = DurationDeserializer::class)
    val waitForPromotionTimeout: Duration = Duration.ofMinutes(5),
)
