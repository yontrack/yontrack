package net.nemerosa.ontrack.extension.casc.entities

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity

interface CascEntityContext {

    /**
     * Runs the [node] configuration against the [entity].
     *
     * @param entity Entity to configure
     * @param node Configuration
     * @param paths Location in the configuration from the initial root
     */
    fun run(entity: ProjectEntity, node: JsonNode, paths: List<String>)

    /**
     * Priority of this context compared to all its siblings.
     *
     * Higher numbers have the higher priority.
     */
    val priority: Int get() = 0
}