package net.nemerosa.ontrack.model.templating

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventRenderer

/**
 * This handler is responsible to render some data.
 */
interface TemplatingContextHandler<T : TemplatingContext> {

    /**
     * ID of this handler
     */
    val id: String

    /**
     * Serializes the data
     */
    fun serialize(data: T): JsonNode = data.asJson()

    /**
     * Deserializes the data
     */
    fun deserialize(data: JsonNode): T

    /**
     * Rendering
     */
    fun render(
        data: T,
        field: String?,
        config: TemplatingSourceConfig,
        renderer: EventRenderer,
    ): String

}