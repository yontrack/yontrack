package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntityType

/**
 * Raw definition of a property value in a repository.
 */
class TProperty(
        val propertyTypeName: String,
        val entityType: ProjectEntityType,
        val entityId: ID,
        val json: JsonNode
)
