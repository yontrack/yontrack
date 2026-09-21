package net.nemerosa.ontrack.extension.notifications.recording

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.model.structure.ProjectEntityID

data class NotificationRecordFilter(
    val offset: Int = 0,
    val size: Int = 10,
    val channel: String? = null,
    val resultType: NotificationResultType? = null,
    val sourceId: String? = null,
    val sourceData: JsonNode? = null,
    @APIDescription("Entity targeted by the event which has triggered the notification")
    val eventEntityId: ProjectEntityID? = null,
)