package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import org.springframework.stereotype.Component

@Component
class GQLRootQueryIndicatorTypes(
        private val indicatorTypes: GQLTypeIndicatorTypes
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
            GraphQLFieldDefinition.newFieldDefinition()
                    .name("indicatorTypes")
                    .description("List of indicator types")
                    .deprecate("Removed in V6. No replacement. See #1893")
                    .type(indicatorTypes.typeRef)
                    .dataFetcher { IndicatorTypes() }
                    .build()

}