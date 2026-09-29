package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolioNotFoundException
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolioService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

@Component
class GQLRootQueryIndicatorPortfolios(
        private val indicatorPortfolio: GQLTypeIndicatorPortfolio,
        private val indicatorPortfolioService: IndicatorPortfolioService
): GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
            GraphQLFieldDefinition.newFieldDefinition()
                    .name("indicatorPortfolios")
                    .description("List of indicator portfolios")
                    .deprecate("Removed in V6. No replacement. See #1893")
                    .type(listType(indicatorPortfolio.typeRef))
                    .argument {
                        it.name(ARG_ID)
                                .description("ID of the indicator portfolio")
                                .type(GraphQLString)
                    }
                    .dataFetcher { env ->
                        val id: String? = env.getArgument(ARG_ID)
                        if (id != null) {
                            val portfolio = indicatorPortfolioService.findPortfolioById(id)
                                    ?: throw IndicatorPortfolioNotFoundException(id)
                            listOf(portfolio)
                        } else {
                            indicatorPortfolioService.findAll()
                        }
                    }
                    .build()

    companion object {
        private const val ARG_ID = "id"
    }

}