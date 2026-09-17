package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLInt
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.labels.ProjectLabelManagementService
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class GQLTypeLabel(
    private val fieldContributors: List<GQLFieldContributor>,
    private val projectLabelManagementService: ProjectLabelManagementService,
    private val structureService: StructureService
) : GQLType {
    override fun getTypeName(): String = Label::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectTypeBuilder(Label::class, cache)
            // List of associated projects
            .field {
                it.name("projects")
                    .description("List of associated projects")
                    .type(listType(GraphQLTypeReference(GQLTypeProject.PROJECT)))
                    .dataFetcher { environment ->
                        val label: Label = environment.getSource()!!
                        val projectIds = projectLabelManagementService.getProjectsForLabel(label)
                        projectIds.map { id ->
                            structureService.getProject(id)
                        }
                    }
            }
            // Number of associated projects
            .field {
                it.name("projectCount")
                    .description("Number of projects carrying this label, among the ones which are visible to the current user")
                    .type(GraphQLNonNull(GraphQLInt))
                    .dataFetcher { environment ->
                        val label: Label = environment.getSource()!!
                        projectLabelManagementService.getProjectsForLabel(label).size
                    }
            }
            // Links
            .fields(Label::class.java.graphQLFieldContributions(fieldContributors))
            // OK
            .build()
}