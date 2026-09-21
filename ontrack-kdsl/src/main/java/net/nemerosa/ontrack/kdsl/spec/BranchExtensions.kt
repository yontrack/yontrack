package net.nemerosa.ontrack.kdsl.spec

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BranchDeletePropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BranchGetPropertyQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BranchSetPropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.BranchFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Creates a [Branch] from a GraphQL [BranchFragment].
 */
fun BranchFragment.toBranch(connected: Connected) = Branch(
    connector = connected.connector,
    project = project?.projectFragment?.toProject(connected) ?: error("Missing parent project"),
    id = id.toUInt(),
    name = name!!,
    description = description,
    disabled = disabled,
)

/**
 * Gets a generic property on a [Branch].
 *
 * @param type FQCN of the property type
 * @return Property raw data as JSON
 */
fun Branch.getProperty(
    type: String,
): JsonNode? =
    graphqlConnector.query(
        BranchGetPropertyQuery(id.toInt(), type)
    )?.branches?.firstOrNull()?.properties?.firstOrNull()?.value?.asJson()

/**
 * Sets a generic property on a [Branch].
 *
 * @param type FQCN of the property type
 * @param data Property raw data (will be converted into JSON)
 */
fun Branch.setProperty(
    type: String,
    data: Any,
): Branch {
    graphqlConnector.mutate(
        BranchSetPropertyMutation(
            id.toInt(),
            type,
            data.asJson()
        )
    ) {
        it?.setBranchPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Deletes a generic property on a [Branch].
 *
 * @param type FQCN of the property type
 */
fun Branch.deleteProperty(
    type: String,
): Branch {
    graphqlConnector.mutate(
        BranchDeletePropertyMutation(
            id.toInt(),
            type
        )
    ) {
        it?.setBranchPropertyById?.payloadUserErrors?.convert()
    }
    return this
}