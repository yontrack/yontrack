package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLInt
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.model.labels.LabelManagementService
import org.springframework.stereotype.Component

/**
 * Gets one label using its ID.
 *
 * This is what the label page is built on: it is reached by its ID alone, from a chip or from the
 * project count of the admin page, and `labels` filters on the category and the name.
 */
@Component
class GQLRootQueryLabel(
    private val label: GQLTypeLabel,
    private val labelManagementService: LabelManagementService,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("label")
            .description("Label using its ID, null when there is no such label")
            .type(label.typeRef)
            .argument {
                it.name(ARG_ID)
                    .description("ID of the label to look for")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .dataFetcher { env ->
                val id: Int = env.getArgument(ARG_ID) ?: throw IllegalStateException("`id` argument is required")
                labelManagementService.findLabelById(id)
            }
            .build()

    companion object {
        const val ARG_ID = "id"
    }
}
