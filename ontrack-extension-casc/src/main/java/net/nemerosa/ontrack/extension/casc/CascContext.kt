package net.nemerosa.ontrack.extension.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.json.schema.JsonTypeProvider

interface CascContext : JsonTypeProvider {
    /**
     * Given a JSON node associated with this context, takes all the mentioned
     * subpaths and registers their content in Ontrack.
     */
    fun run(node: JsonNode, paths: List<String>)

    /**
     * Renders this context as JSON in order to generate the current settings.
     */
    fun render(): JsonNode

    /**
     * Priority of this context compared to all its siblings.
     *
     * Higher numbers have the higher priority.
     */
    val priority: Int get() = 0

}