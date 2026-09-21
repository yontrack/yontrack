package net.nemerosa.ontrack.kdsl.spec

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ProjectEntityType

/**
 * Representation of a validation stamp.
 *
 * @property connector Ontrack connector
 * @property id Validation stamp ID
 * @property name Validation stamp name
 * @property description Validation stamp description
 */
class ValidationStamp(
    connector: Connector,
    id: UInt,
    val name: String,
    val description: String?,
    val dataType: String?,
    val dataTypeConfig: JsonNode?,
) : ProjectEntity(connector, ProjectEntityType.VALIDATION_STAMP, id)
