package net.nemerosa.ontrack.kdsl.spec

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreateLabelMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.DeleteLabelMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.LabelListQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.UpdateLabelMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.LabelFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Creates a [Label] from a GraphQL [LabelFragment].
 */
fun LabelFragment.toLabel() = Label(
    id = id,
    category = category,
    name = name,
    description = description,
    color = color,
)

/**
 * Creates a label.
 *
 * Needs the `LabelManagement` global function.
 *
 * @param name Name of the label
 * @param category Category of the label, optional
 * @param description Description of the label
 * @param color Color of the label, in the `#RRGGBB` format
 * @return Created label
 */
fun Ontrack.createLabel(
    name: String,
    category: String? = null,
    description: String? = null,
    color: String = "#000000",
): Label =
    graphqlConnector.mutate(
        CreateLabelMutation(
            Optional.presentIfNotNull(category),
            name,
            Optional.presentIfNotNull(description),
            color,
        )
    ) {
        it?.createLabel?.payloadUserErrors?.convert()
    }?.checkData {
        it.createLabel?.label
    }
        ?.labelFragment?.toLabel()
        ?: throw GraphQLMissingDataException("Did not get back the created label")

/**
 * Updates an existing label. Every field is replaced, not merged.
 *
 * Needs the `LabelManagement` global function.
 *
 * @param id ID of the label to update
 * @return Updated label
 */
fun Ontrack.updateLabel(
    id: Int,
    name: String,
    category: String? = null,
    description: String? = null,
    color: String = "#000000",
): Label =
    graphqlConnector.mutate(
        UpdateLabelMutation(
            id,
            Optional.presentIfNotNull(category),
            name,
            Optional.presentIfNotNull(description),
            color,
        )
    ) {
        it?.updateLabel?.payloadUserErrors?.convert()
    }?.checkData {
        it.updateLabel?.label
    }
        ?.labelFragment?.toLabel()
        ?: throw GraphQLMissingDataException("Did not get back the updated label")

/**
 * Deletes a label. Its assignments to projects go with it.
 *
 * Needs the `LabelManagement` global function.
 *
 * @param id ID of the label to delete
 */
fun Ontrack.deleteLabel(id: Int) {
    graphqlConnector.mutate(
        DeleteLabelMutation(id)
    ) {
        it?.deleteLabel?.payloadUserErrors?.convert()
    }
}

/**
 * Gets the list of all labels.
 */
fun Ontrack.labels(): List<Label> =
    graphqlConnector.query(
        LabelListQuery()
    )?.labels?.map {
        it.labelFragment.toLabel()
    } ?: emptyList()
