package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.environments.EnvironmentMatrixFilter
import net.nemerosa.ontrack.extension.environments.service.EnvironmentMatrixService
import net.nemerosa.ontrack.graphql.schema.GQLInputType
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component

/**
 * `environmentMatrix` - the one query the Environments home makes.
 *
 * Everything the screen shows comes from here: the columns, the rows, the cells and the paging. The
 * alternative it replaces - `environments { slots { ... } }` - reads the same data the wrong way
 * round, one environment at a time, and cannot page projects at all.
 */
@Component
class GQLRootQueryEnvironmentMatrix(
    private val gqlTypeEnvironmentMatrix: GQLTypeEnvironmentMatrix,
    private val gqlInputEnvironmentMatrixFilter: GQLInputEnvironmentMatrixFilter,
    private val environmentMatrixService: EnvironmentMatrixService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("environmentMatrix")
            .description("Projects x environments, as the Environments home draws them")
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_FILTER)
                    .description("What narrows the matrix down. Applied before paging.")
                    .type(gqlInputEnvironmentMatrixFilter.typeRef)
                    .build()
            )
            .argument(intArgument(ARG_OFFSET, "Index of the first project to return"))
            .argument(
                intArgument(
                    ARG_SIZE,
                    "Number of projects to return (defaults to ${EnvironmentMatrixService.DEFAULT_SIZE})"
                )
            )
            .type(gqlTypeEnvironmentMatrix.typeRef.toNotNull())
            .dataFetcher { env ->
                val filter = env.getArgument<Any?>(ARG_FILTER)
                    ?.let { gqlInputEnvironmentMatrixFilter.convert(it) }
                    ?: EnvironmentMatrixFilter()
                environmentMatrixService.matrix(
                    filter = filter,
                    offset = env.getArgument(ARG_OFFSET) ?: 0,
                    size = env.getArgument(ARG_SIZE) ?: EnvironmentMatrixService.DEFAULT_SIZE,
                )
            }
            .build()

    companion object {
        private const val ARG_FILTER = "filter"
        private const val ARG_OFFSET = "offset"
        private const val ARG_SIZE = "size"
    }

}

/**
 * The matrix filter as a GraphQL input.
 */
@Component
class GQLInputEnvironmentMatrixFilter : GQLInputType<EnvironmentMatrixFilter> {

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        GraphQLBeanConverter.asInputType(EnvironmentMatrixFilter::class, dictionary)

    override fun convert(argument: Any?): EnvironmentMatrixFilter? =
        argument?.asJson()?.parse()

    override fun getTypeRef() = GraphQLTypeReference(
        EnvironmentMatrixFilter::class.java.simpleName
    )
}
