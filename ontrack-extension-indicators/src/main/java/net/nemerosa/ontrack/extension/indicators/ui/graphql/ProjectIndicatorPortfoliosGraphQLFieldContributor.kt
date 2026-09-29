package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolioService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * Access to the portfolios associated with a project (through its labels).
 */
@Component
class ProjectIndicatorPortfoliosGraphQLFieldContributor(
        private val indicatorPortfolio: GQLTypeIndicatorPortfolio,
        private val indicatorPortfolioService: IndicatorPortfolioService
) : GQLProjectEntityFieldContributor {

    override fun getFields(projectEntityClass: Class<out ProjectEntity>, projectEntityType: ProjectEntityType): List<GraphQLFieldDefinition>? {
        return if (projectEntityType == ProjectEntityType.PROJECT) {
            listOf(
                    GraphQLFieldDefinition.newFieldDefinition()
                            .name("indicatorPortfolios")
                            .description("List of indicator portfolios associated with this project, through its labels.")
                            .deprecate("Removed in V6. No replacement. See #1893")
                            .type(listType(indicatorPortfolio.typeRef))
                            .dataFetcher { env ->
                                val project: Project = env.getSource()!!
                                indicatorPortfolioService.findPortfolioByProject(project)
                            }
                            .build()
            )
        } else {
            null
        }
    }

}