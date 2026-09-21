package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.environments.EnvironmentFilter
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

@Component
class GQLRootQueryEnvironments(
    private val gqlInputEnvironmentFilter: GQLInputEnvironmentFilter,
    private val gqlTypeEnvironment: GQLTypeEnvironment,
    private val environmentService: EnvironmentService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("environments")
            .description("List of environments")
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_FILTER)
                    .description("Filter on the environments")
                    .type(gqlInputEnvironmentFilter.typeRef)
                    .build()
            )
            .type(listType(gqlTypeEnvironment.typeRef))
            .dataFetcher { env ->
                val filterInput = env.getArgument<Any>(ARG_FILTER)
                val filter = filterInput?.let { gqlInputEnvironmentFilter.convert(it) }
                    ?: EnvironmentFilter()
                environmentService.findAll(filter)
            }
            .build()

    companion object {
        private const val ARG_FILTER = "filter"
    }

}