package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.graphql.schema.GraphqlSchemaService
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.stereotype.Component

@Component
class GraphQLCustomization(
        private val graphqlSchemaService: GraphqlSchemaService,
) : GraphQlSourceBuilderCustomizer {

    override fun customize(builder: GraphQlSource.SchemaResourceBuilder) {
        builder.schemaFactory { typeDefinitionRegistry, runtimeWiring ->
            graphqlSchemaService.createSchema(typeDefinitionRegistry, runtimeWiring)
        }
    }

}