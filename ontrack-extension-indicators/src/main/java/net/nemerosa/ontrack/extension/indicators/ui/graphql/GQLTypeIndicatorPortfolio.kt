package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCategoryService
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolio
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolioService
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorViewService
import net.nemerosa.ontrack.extension.indicators.stats.IndicatorStatsService
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeIndicatorPortfolio(
        private val indicatorPortfolioService: IndicatorPortfolioService,
        private val label: GQLTypeLabel,
        private val indicatorCategory: GQLTypeIndicatorCategory,
        private val indicatorCategoryService: IndicatorCategoryService,
        private val indicatorCategoryStats: GQLTypeIndicatorCategoryStats,
        private val indicatorStatsService: IndicatorStatsService,
        private val indicatorViewService: IndicatorViewService,
        private val fieldContributors: List<GQLFieldContributor>
) : GQLType {

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
            GraphQLObjectType.newObject()
                    .name(typeName)
                    .description("Grouping indicators for a group of projects identified by labels.")
                    .stringField("id", "ID of the portfolio")
                    .stringField("name", "Name of the portfolio")
                    // Label for this portfolio
                    .field {
                        it.name(IndicatorPortfolio::label.name)
                                .description("Label for this portfolio")
                                .type(label.typeRef)
                                .dataFetcher { env ->
                                    val portfolio: IndicatorPortfolio = env.getSource()!!
                                    indicatorPortfolioService.getPortfolioLabel(portfolio)
                                }
                    }
                    // Categories
                    .field {
                        it.name(IndicatorPortfolio::categories.name)
                                .description("Indicator categories being shown for this portfolio")
                                .type(listType(indicatorCategory.typeRef))
                                .dataFetcher { env ->
                                    val portfolio: IndicatorPortfolio = env.getSource()!!
                                    portfolio.categories.mapNotNull { id ->
                                        indicatorCategoryService.findCategoryById(id)
                                    }
                                }
                    }
                    // Associated projects
                    .field {
                        it.name("projects")
                                .description("List of projects associated with this portfolio")
                                .type(listType(GraphQLTypeReference(GQLTypeProject.PROJECT)))
                                .dataFetcher { env ->
                                    val portfolio: IndicatorPortfolio = env.getSource()!!
                                    indicatorPortfolioService.getPortfolioProjects(portfolio)
                                }
                    }
                    // Categories and stats
                    .field {
                        it.name("categoryStats")
                                .description("Stats per category")
                                .type(listType(indicatorCategoryStats.typeRef))
                                .deprecate("Use viewStats with viewId = null. This field will be removed in V5.")
                                .durationArgument()
                                .dataFetcher { env ->
                                    val duration = env.getDurationArgument()
                                    val portfolio: IndicatorPortfolio = env.getSource()!!
                                    indicatorStatsService.getStatsPortfolio(portfolio, duration)
                                }
                    }
                    // Stats for a view
                    .field {
                        it.name("viewStats")
                            .description("Indicators stats for a given view for this portfolio")
                            .durationArgument()
                            .argument { arg ->
                                arg.name("id")
                                    .description("ID of the indicator view, set to null to use the portfolio default configuration")
                                    .type(GraphQLString)
                            }
                            .type(GraphQLList(GraphQLNonNull(indicatorCategoryStats.typeRef)))
                            .dataFetcher { env ->
                                val portfolio: IndicatorPortfolio = env.getSource()!!
                                val duration = env.getDurationArgument()
                                val id: String? = env.getArgument<String>("id")?.takeIf { it.isNotBlank() }
                                val view = if (id != null) {
                                    indicatorViewService.findIndicatorViewById(id)
                                        // Not returning any stat if no view
                                        ?: return@dataFetcher null
                                } else {
                                    // The portfolio categories will be used
                                    null
                                }
                                indicatorStatsService.getPortfolioViewStats(portfolio, view, duration)
                            }
                    }
                    // Links
                    .fields(IndicatorPortfolio::class.java.graphQLFieldContributions(fieldContributors))
                    //OK
                    .build()

    override fun getTypeName(): String = IndicatorPortfolio::class.java.simpleName
}