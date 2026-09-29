package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.indicators.computing.ConfigurableIndicatorService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

@Component
class GQLRootQueryConfigurableIndicators(
    private val gqlConfigurableIndicatorTypeState: GQLTypeConfigurableIndicatorTypeState,
    private val configurableIndicatorService: ConfigurableIndicatorService,
): GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
        .name("configurableIndicators")
        .description("List of configurable indicators")
        .deprecate("Removed in V6. No replacement. See #1893")
        .argument {
            it.name("category")
                .description("Filter the indicators on their category ID")
                .type(GraphQLString)
        }
        .argument {
            it.name("type")
                .description("Filter the indicators on their type ID")
                .type(GraphQLString)
        }
        .type(listType(gqlConfigurableIndicatorTypeState.typeRef))
        .dataFetcher { env ->
            val category: String? = env.getArgument("category")
            val type: String? = env.getArgument("type")
            configurableIndicatorService.getConfigurableIndicatorStates().filter {
                category == null || it.type.category.id == category
            }.filter {
                type == null || it.type.id == type
            }
        }
        .build()
}