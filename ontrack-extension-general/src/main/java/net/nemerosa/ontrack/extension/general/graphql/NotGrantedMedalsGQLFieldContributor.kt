package net.nemerosa.ontrack.extension.general.graphql

import graphql.Scalars.GraphQLBoolean
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.extension.general.NotGrantedMedalsService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Branch.notGrantedMedals`: resolved here because a client cannot do it, the `settings` query being
 * restricted to `GlobalSettings`, which an ordinary reader does not have.
 */
@Component
class NotGrantedMedalsGQLFieldContributor(
    private val notGrantedMedalsService: NotGrantedMedalsService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.BRANCH) {
        listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("notGrantedMedals")
                .description(
                    "Whether the branch builds view shows a medal for each auto-promoted level a build does not " +
                            "have yet: the first of the property of the branch, the property of the project and " +
                            "the global settings carrying a value decides."
                )
                .type(GraphQLNonNull(GraphQLBoolean))
                .dataFetcher { env ->
                    val branch: Branch = env.getSource()!!
                    notGrantedMedalsService.isEnabled(branch)
                }
                .build()
        )
    } else {
        null
    }
}
