package net.nemerosa.ontrack.extension.casc.entities

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity

/**
 * Applying some Casc code for an entity.
 */
interface CascEntityService {

    fun apply(entity: ProjectEntity, node: JsonNode)

}