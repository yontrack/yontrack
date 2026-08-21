package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.stringListArgument
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * `scmChangeLog` query to get a change log between two builds.
 */
@Component
class GQLRootQuerySCMChangeLog(
    private val gqlTypeSCMChangeLog: GQLTypeSCMChangeLog,
    private val structureService: StructureService,
    private val scmChangeLogService: SCMChangeLogService,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
        .name("scmChangeLog")
        .description("Query to get a change log between two builds.")
        .argument(intArgument(ARG_FROM, "ID of the build from", nullable = false))
        .argument(intArgument(ARG_TO, "ID of the build to", nullable = false))
        .argument(
            stringListArgument(
                ARG_PROJECTS,
                """
                    List of projects to follow one by one for a get deep change log. Each item
                    in the list is either a project name, or a project name and qualifier separated
                    by a colon (:).
                    """.trimIndent(),
                nullable = true
            )
        )
        .type(gqlTypeSCMChangeLog.typeRef)
        .dataFetcher { env ->
            val from: Int = env.getArgument(ARG_FROM)!!
            val to: Int = env.getArgument(ARG_TO)!!
            val projects: List<String>? = env.getArgument(ARG_PROJECTS)

            scmChangeLogService.getChangeLogForBoundaries(
                from = structureService.getBuild(ID.of(from)),
                to = structureService.getBuild(ID.of(to)),
                projects = projects,
            )
        }
        .build()

    companion object {
        const val ARG_FROM = "from"
        const val ARG_TO = "to"
        const val ARG_PROJECTS = "projects"
    }
}
