package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.indicators.ui.ProjectIndicators
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

@Component
class ProjectIndicatorsGraphQLFieldContributor(
        private val projectIndicators: GQLTypeProjectIndicators
) : GQLProjectEntityFieldContributor {

    override fun getFields(projectEntityClass: Class<out ProjectEntity>, projectEntityType: ProjectEntityType): List<GraphQLFieldDefinition>? {
        return if (projectEntityType == ProjectEntityType.PROJECT) {
            listOf(
                    GraphQLFieldDefinition.newFieldDefinition()
                            .name("projectIndicators")
                            .description("List of project indicators")
                            .deprecate("Removed in V6. No replacement. See #1893")
                            .type(projectIndicators.typeRef)
                            .dataFetcher { env ->
                                val project: Project = env.getSource()!!
                                ProjectIndicators(project)
                            }
                            .build()
            )
        } else {
            null
        }
    }

}