package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.graphql.support.stringListArgument
import net.nemerosa.ontrack.model.exceptions.BranchNotFoundException
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

/**
 * `scmChangeLogByName` query to get a change log between two builds, identified by their
 * name or display name inside a project, instead of by their IDs.
 *
 * This is the query behind the change log permalinks which can be written by hand.
 */
@Component
class GQLRootQuerySCMChangeLogByName(
    private val gqlTypeSCMChangeLog: GQLTypeSCMChangeLog,
    private val structureService: StructureService,
    private val buildDisplayNameService: BuildDisplayNameService,
    private val scmChangeLogService: SCMChangeLogService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
        .name("scmChangeLogByName")
        .description("Query to get a change log between two builds, using their names or display names.")
        .argument(stringArgument(ARG_PROJECT, "Name of the project", nullable = false))
        .argument(stringArgument(ARG_FROM, "Name or display name of the build from", nullable = false))
        .argument(stringArgument(ARG_TO, "Name or display name of the build to", nullable = false))
        .argument(
            stringArgument(
                ARG_FROM_BRANCH,
                "Name of the branch of the build from, used to disambiguate its name",
                nullable = true
            )
        )
        .argument(
            stringArgument(
                ARG_TO_BRANCH,
                "Name of the branch of the build to, used to disambiguate its name",
                nullable = true
            )
        )
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
            val projectName: String = env.getArgument(ARG_PROJECT)!!
            val from: String = env.getArgument(ARG_FROM)!!
            val to: String = env.getArgument(ARG_TO)!!
            val fromBranch: String? = env.getArgument(ARG_FROM_BRANCH)
            val toBranch: String? = env.getArgument(ARG_TO_BRANCH)
            val projects: List<String>? = env.getArgument(ARG_PROJECTS)

            // A project which is not visible is reported as not existing, so that this query
            // cannot be used to check the existence of a project.
            val project = structureService.findProjectByName(projectName).getOrNull()
                ?: throw ProjectNotFoundException(projectName)

            scmChangeLogService.getChangeLogForBoundaries(
                from = findBuild(project, from, fromBranch),
                to = findBuild(project, to, toBranch),
                projects = projects,
            )
        }
        .build()

    /**
     * Finds a build in a [project] using its [name], which can be its build name or its display name.
     *
     * When a [branchName] is given, the build name is looked for inside this branch only. Note that
     * the display name remains looked for inside the whole project, like when resolving the target of
     * a notification.
     */
    private fun findBuild(project: Project, name: String, branchName: String?): Build =
        if (branchName.isNullOrBlank()) {
            buildDisplayNameService.findBuildByDisplayName(
                project = project,
                name = name,
                onlyDisplayName = false,
            ) ?: throw BuildNotFoundException(project.name, name)
        } else {
            val branch = structureService.findBranchByName(project.name, branchName).getOrNull()
                ?: throw BranchNotFoundException(project.name, branchName)
            structureService.findBuildByName(project.name, branch.name, name).getOrNull()
                ?: buildDisplayNameService.findBuildByDisplayName(
                    project = project,
                    name = name,
                    onlyDisplayName = true,
                )
                ?: throw BuildNotFoundException(project.name, branch.name, name)
        }

    companion object {
        const val ARG_PROJECT = "project"
        const val ARG_FROM = "from"
        const val ARG_TO = "to"
        const val ARG_FROM_BRANCH = "fromBranch"
        const val ARG_TO_BRANCH = "toBranch"
        const val ARG_PROJECTS = "projects"
    }
}
