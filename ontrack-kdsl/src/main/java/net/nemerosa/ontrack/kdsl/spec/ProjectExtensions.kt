package net.nemerosa.ontrack.kdsl.spec

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectDeletePropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectGetPropertyQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectLabelsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectSetPropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SetProjectLabelsMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.ProjectFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Creates a [Project] from a GraphQL [ProjectFragment].
 */
fun ProjectFragment.toProject(connected: Connected) = Project(
    connector = connected.connector,
    id = id.toUInt(),
    name = name!!,
    description = description,
)

/**
 * Gets a generic property on a [Project].
 *
 * @param type FQCN of the property type
 * @return Property raw data as JSON
 */
fun Project.getProperty(
    type: String,
): JsonNode? =
    graphqlConnector.query(
        ProjectGetPropertyQuery(id.toInt(), type)
    )?.projects?.firstOrNull()?.properties?.firstOrNull()?.value?.asJson()

/**
 * Sets a generic property on a [Project].
 *
 * @param type FQCN of the property type
 * @param data Property raw data (will be converted into JSON)
 */
fun Project.setProperty(
    type: String,
    data: Any,
): Project {
    graphqlConnector.mutate(
        ProjectSetPropertyMutation(
            id.toInt(),
            type,
            data.asJson()
        )
    ) {
        it?.setProjectPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Deletes a generic property on a [Project].
 *
 * @param type FQCN of the property type
 */
fun Project.deleteProperty(
    type: String,
): Project {
    graphqlConnector.mutate(
        ProjectDeletePropertyMutation(
            id.toInt(),
            type
        )
    ) {
        it?.setProjectPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Gets the labels of a [Project].
 */
fun Project.labels(): List<Label> =
    graphqlConnector.query(
        ProjectLabelsQuery(id.toInt())
    )?.projects?.firstOrNull()?.labels?.map {
        it.labelFragment.toLabel()
    } ?: emptyList()

/**
 * Sets the labels of a [Project], replacing all the existing ones.
 *
 * Passing an empty list removes every label from the project.
 *
 * Needs the `ProjectLabelManagement` project function.
 *
 * @param labelIds IDs of the labels to set on the project
 */
fun Project.setLabels(
    labelIds: List<Int>,
): Project {
    graphqlConnector.mutate(
        SetProjectLabelsMutation(
            id.toInt(),
            labelIds,
        )
    ) {
        it?.setProjectLabels?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Sets the labels of a [Project] from [Label] objects, replacing all the existing ones.
 */
fun Project.setLabels(
    vararg labels: Label,
): Project = setLabels(labels.map { it.id })