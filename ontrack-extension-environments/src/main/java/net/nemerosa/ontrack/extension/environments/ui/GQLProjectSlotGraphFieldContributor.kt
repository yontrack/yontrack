package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.environments.EnvironmentsLicense
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.service.graph.ProjectSlotGraphService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

@Component
class GQLProjectSlotGraphFieldContributor(
    private val environmentsLicense: EnvironmentsLicense,
    private val gqlTypeSlotGraph: GQLTypeSlotGraph,
    private val projectSlotGraphService: ProjectSlotGraphService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType
    ): List<GraphQLFieldDefinition>? =
        if (projectEntityType == ProjectEntityType.PROJECT && environmentsLicense.environmentFeatureEnabled) {
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("slotGraph")
                    .description("Graph of environment slots for a project")
                    .type(gqlTypeSlotGraph.typeRef.toNotNull())
                    .argument(
                        stringArgument(
                            ARG_QUALIFIER,
                            description = "Qualifier for the slots",
                            defaultValue = Slot.DEFAULT_QUALIFIER
                        )
                    )
                    .dataFetcher { env ->
                        val project: Project = env.getSource()!!
                        val qualifier = env.getArgument<String>(ARG_QUALIFIER) ?: Slot.DEFAULT_QUALIFIER
                        projectSlotGraphService.slotGraph(project, qualifier)
                    }
                    .build()
            )
        } else {
            null
        }

    companion object {
        private const val ARG_QUALIFIER = "qualifier"
    }
}