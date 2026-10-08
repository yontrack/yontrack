package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Build.assistedChange`
 */
@Component
class AssistedChangeBuildGraphQLFieldContributor(
    private val gqlTypeAssistedChange: GQLTypeAssistedChange,
    private val assistedChangeService: AssistedChangeService,
) : GQLProjectEntityFieldContributor {
    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType
    ): List<GraphQLFieldDefinition>? =
        if (projectEntityType == ProjectEntityType.BUILD) {
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("assistedChange")
                    .description("Assisted change of the build: whether its commits since the previous build on its branch were written with assistants. Null when it has been neither computed nor set yet, which counts as unknown.")
                    .type(gqlTypeAssistedChange.typeRef)
                    .dataFetcher { env ->
                        val build: Build = env.getSource()!!
                        assistedChangeService.getAssistedChange(build)
                    }
                    .build()
            )
        } else {
            null
        }
}
