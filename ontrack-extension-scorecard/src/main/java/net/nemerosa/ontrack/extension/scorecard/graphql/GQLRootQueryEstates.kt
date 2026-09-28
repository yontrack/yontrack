package net.nemerosa.ontrack.extension.scorecard.graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scorecard.estates.Estate
import net.nemerosa.ontrack.extension.scorecard.estates.EstateService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringArgument
import org.springframework.stereotype.Component

/**
 * `estates`: all the estates, by name.
 */
@Component
class GQLRootQueryEstates(
    private val estateService: EstateService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("estates")
            .description("All the estates, by name. Needs the licensed feature \"Delivery scorecard\".")
            .type(listType(GraphQLTypeReference(Estate::class.java.simpleName)))
            .dataFetcher { estateService.findAll() }
            .build()
}

/**
 * `estate(name)`: one estate, by name.
 */
@Component
class GQLRootQueryEstate(
    private val estateService: EstateService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("estate")
            .description("Estate by name, null if none. Needs the licensed feature \"Delivery scorecard\".")
            .argument(stringArgument(ARG_NAME, "Name of the estate", nullable = false))
            .type(GraphQLTypeReference(Estate::class.java.simpleName))
            .dataFetcher { env ->
                val name: String = env.getArgument(ARG_NAME)!!
                estateService.findByName(name)
            }
            .build()

    companion object {
        private const val ARG_NAME = "name"
    }
}
