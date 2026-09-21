package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class GQLRootQueryPaginatedProjects(
    private val structureService: StructureService,
    private val gqlPaginatedListFactory: GQLPaginatedListFactory,
    private val project: GQLTypeProject,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        gqlPaginatedListFactory.createRootPaginatedField(
            cache = GQLTypeCache(),
            fieldName = "paginatedProjects",
            fieldDescription = "Paginated list of projects",
            itemType = project.typeName,
            arguments = listOf(
                stringArgument("name", "Fragment of the project name to filter on"),
                GraphQLArgument.newArgument()
                    .name(ARG_LABELS)
                    .description(
                        "Labels the projects must all carry, as `category:name` display strings " +
                                "(just `name` for a label without a category), like the `labels` argument of `projects`. " +
                                "Combined with `name` by AND, and so are the labels between themselves."
                    )
                    .type(GraphQLList(GraphQLNonNull(GraphQLString)))
                    .build(),
            ),
            itemPaginatedListProvider = { env, offset, size ->
                val name: String? = env.getArgument("name")
                val labels: List<String> = env.getArgument<List<String>>(ARG_LABELS) ?: emptyList()
                // The filtering is done on the whole list of projects, before the page is
                // extracted - filtering the page would give a wrong total and hide items.
                val items = structureService.findProjects(name, labels)
                PaginatedList.create(
                    items = items,
                    offset = offset,
                    pageSize = size,
                )
            }
        )

    companion object {
        const val ARG_LABELS = "labels"
    }
}
