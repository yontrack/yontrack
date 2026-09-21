package net.nemerosa.ontrack.extension.notifications.queue

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.notifications.model.NotificationSourceData
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.support.NameValue

data class NotificationQueuePayload(
    val id: String,
    val source: NotificationSourceData,
    val channel: String,
    val channelConfig: JsonNode,
    val serializableEvent: SerializableEvent,
    val template: String?,
) {
    val eventType: String = serializableEvent.eventType
    val signature: Signature? = serializableEvent.signature
    val entities: Map<ProjectEntityType, Int> = serializableEvent.entities
    val extraEntities: Map<ProjectEntityType, Int> = serializableEvent.extraEntities
    val ref: ProjectEntityType? = serializableEvent.ref
    val values: Map<String, NameValue> = serializableEvent.values
}
