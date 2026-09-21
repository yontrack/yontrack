package net.nemerosa.ontrack.extension.notifications.recording

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.model.NotificationSourceData
import java.time.LocalDateTime

data class NotificationRecord(
    @APIDescription("Record unique ID")
    val id: String,
    @APIDescription("Source of the notification")
    val source: NotificationSourceData?,
    @APIDescription("Record timestamp")
    val timestamp: LocalDateTime,
    @APIDescription("Channel type")
    val channel: String,
    @APIDescription("Channel configuration")
    val channelConfig: JsonNode,
    @APIDescription("Event being notified")
    val event: JsonNode,
    @APIDescription("Result of the notification")
    val result: NotificationRecordResult,
)
