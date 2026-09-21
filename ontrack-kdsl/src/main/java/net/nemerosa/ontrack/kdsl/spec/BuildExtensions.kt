package net.nemerosa.ontrack.kdsl.spec

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildDeletePropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildGetPropertyQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildSetPropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.BuildFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Creates a [Build] from a GraphQL [BuildFragment].
 */
fun BuildFragment.toBuild(connected: Connected) = Build(
    connector = connected.connector,
    branch = branch?.branchFragment?.toBranch(connected) ?: error("Missing parent branch"),
    id = id.toUInt(),
    name = name!!,
    description = description,
)

/**
 * Sets a generic property on a [Build].
 *
 * @param type FQCN of the property type
 * @param data Property raw data (will be converted into JSON)
 */
fun Build.setProperty(
    type: String,
    data: Any,
): Build {
    graphqlConnector.mutate(
        BuildSetPropertyMutation(
            id.toInt(),
            type,
            data.asJson()
        )
    ) {
        it?.setBuildPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Deletes a generic property on a [Build].
 *
 * @param type FQCN of the property type
 */
fun Build.deleteProperty(
    type: String
): Build {
    graphqlConnector.mutate(
        BuildDeletePropertyMutation(
            id.toInt(),
            type
        )
    ) {
        it?.setBuildPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Gets a generic property on a [Build].
 *
 * @param type FQCN of the property type
 * @return Property raw data as JSON
 */
fun Build.getProperty(
    type: String,
): JsonNode? =
    graphqlConnector.query(
        BuildGetPropertyQuery(id.toInt(), type)
    )?.builds?.firstOrNull()?.properties?.firstOrNull()?.value?.asJson()
