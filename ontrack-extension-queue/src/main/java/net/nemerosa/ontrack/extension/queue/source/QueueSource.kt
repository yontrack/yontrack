package net.nemerosa.ontrack.extension.queue.source

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Contains information about the source of a queue message.
 *
 * @param feature ID of the [ExtensionFeature] which provides the information.
 * @param id ID of the [QueueSourceExtension] which provides the information.
 * @param data JSON data as understood by the [QueueSourceExtension]
 */
data class QueueSource(
        @APIDescription("ID of the ExtensionFeature which provides the information.")
        val feature: String,
        @APIDescription("ID of the QueueSourceExtension which provides the information.")
        val id: String,
        @APIDescription("JSON data as understood by the QueueSourceExtension")
        val data: JsonNode,
)
