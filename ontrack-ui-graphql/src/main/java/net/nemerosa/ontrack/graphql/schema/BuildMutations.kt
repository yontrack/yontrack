package net.nemerosa.ontrack.graphql.schema

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.graphql.support.TypeRef
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.exceptions.BranchNotFoundException
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Component
class BuildMutations(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val runInfoService: RunInfoService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        /**
         * Creating a build
         */
        simpleMutation(
            name = CREATE_BUILD,
            description = "Creates a new build",
            input = CreateBuildInput::class,
            outputName = "build",
            outputDescription = "Created build",
            outputType = Build::class
        ) { input ->
            val branch = getBranch(input)
            val build = structureService.newBuild(
                Build.of(
                    branch = branch,
                    nameDescription = NameDescription(input.name, input.description),
                    signature = securityService.currentSignature
                )
            )
            // Run info
            if (input.runInfo != null) {
                runInfoService.setRunInfo(build, input.runInfo)
            }
            // OK
            build
        },
        /**
         * Updating a build
         */
        simpleMutation(
            name = "updateBuild",
            description = "Updates an existing build",
            input = UpdateBuildInput::class,
            outputName = "build",
            outputDescription = "Updated build",
            outputType = Build::class
        ) { input ->
            var build = structureService.getBuild(ID.of(input.id))
            var changed = false
            // Creation timestamp
            if (input.creation != null) {
                build = build.withSignature(
                    build.signature.withTime(input.creation)
                )
                changed = true
            }
            // Name
            if (!input.name.isNullOrBlank()) {
                build = build.withName(input.name)
                changed = true
            }
            // Description
            if (input.description != null) {
                build = build.withDescription(input.description)
                changed = true
            }
            // Saving it
            if (changed) {
                build = structureService.saveBuild(build)
            }
            // OK
            build
        },
        /**
         * Creating a build or getting it if it already exists
         */
        simpleMutation(
            name = CREATE_BUILD_OR_GET,
            description = "Creates a new build or gets it if it already exists",
            input = CreateBuildOrGetInput::class,
            outputName = "build",
            outputDescription = "Created or existing build",
            outputType = Build::class
        ) { input ->
            createBuildOrGet(input)
        },
        /**
         * Creating a link between two builds using their names
         */
        simpleMutation(
            name = "linkBuild",
            description = "Link two builds using their names",
            input = LinkBuildInput::class,
            outputName = "build",
            outputDescription = "Build linked from",
            outputType = Build::class
        ) { input ->
            val from = findBuildByProjectAndName(input.fromProject, input.fromBuild)
            val to = findBuildByProjectAndName(input.toProject, input.toBuild)
            if (from != null && to != null) {
                structureService.createBuildLink(from, to, input.qualifier ?: BuildLink.DEFAULT)
            }
            // Return the origin
            from
        },
        /**
         * Creating a link between one build and several other ones using their names
         */
        simpleMutation(
            name = "linksBuild",
            description = "Link build to other ones using their names",
            input = LinksBuildInput::class,
            outputName = "build",
            outputDescription = "Build linked from",
            outputType = Build::class
        ) { input ->
            val from = findBuildByProjectAndName(input.fromProject, input.fromBuild)
            if (from != null) {
                input.links.forEach { link ->
                    val to = findBuildByProjectAndName(link.project, link.build)
                    if (to != null) {
                        structureService.createBuildLink(from, to, link.qualifier ?: BuildLink.DEFAULT)
                    }
                }
            }
            // Return the origin
            from
        },
        /**
         * Creating a link between two builds using their IDs
         */
        simpleMutation(
            name = "linkBuildById",
            description = "Link two builds using their IDs",
            input = LinkBuildByIdInput::class,
            outputName = "build",
            outputDescription = "Build linked from",
            outputType = Build::class
        ) { input ->
            val from = structureService.getBuild(ID.of(input.fromBuild))
            val to = structureService.getBuild(ID.of(input.toBuild))
            structureService.createBuildLink(from, to, input.qualifier ?: BuildLink.DEFAULT)
            // Return the origin
            from
        },
        unitMutation(
            name = "deleteBuildById",
            description = "Deletes a build by ID",
            input = DeleteBuildByIdInput::class
        ) { input ->
            structureService.deleteBuild(ID.of(input.id))
        },
        /**
         * Deleting build links from a build, optionally filtered by target project and/or qualifier
         */
        unitMutation(
            name = "deleteBuildLinks",
            description = "Deletes build links from a build, optionally filtered by target project and/or qualifier",
            input = DeleteBuildLinksInput::class
        ) { input ->
            val from = structureService.getBuild(ID.of(input.fromBuild))
            val links = structureService.getQualifiedBuildsUsedBy(from, size = Int.MAX_VALUE / 2)
            links.pageItems
                .filter { input.project == null || it.build.project.name == input.project }
                .filter { input.qualifier == null || it.qualifier == input.qualifier }
                .forEach { structureService.deleteBuildLink(from, it.build, it.qualifier) }
        },
    )


    private fun createBuildOrGet(input: CreateBuildOrGetInput): Build {
        val branch = getBranch(input)
        val build = structureService.findBuildByName(branch.project.name, branch.name, input.name).getOrNull()
            ?: structureService.newBuild(
                Build.of(
                    branch = branch,
                    nameDescription = NameDescription(input.name, input.description),
                    signature = securityService.currentSignature
                )
            )
        // Run info
        if (input.runInfo != null) {
            runInfoService.setRunInfo(build, input.runInfo)
        }
        // OK
        return build
    }

    private fun findBuildByProjectAndName(projectName: String, buildName: String): Build? {
        val project = structureService.findProjectByName(projectName).getOrNull() ?: return null
        val builds = structureService.buildSearch(
            project.id,
            BuildSearchForm(maximumCount = 1, buildName = buildName, buildExactMatch = true)
        )
        return builds.firstOrNull()
    }

    companion object {
        const val CREATE_BUILD = "createBuild"
        const val CREATE_BUILD_OR_GET = "createBuildOrGet"
    }

    private fun getBranch(input: BuildInput): Branch {
        return if (input.branchId != null) {
            checkInput(input.projectName.isNullOrBlank(), "Since branchId is provided, projectName is not required.")
            checkInput(input.projectId == null, "Since branchId is provided, projectId is not required.")
            checkInput(input.branchName.isNullOrBlank(), "Since branchId is provided, branchName is not required.")
            structureService.getBranch(ID.of(input.branchId as Int))
        } else {
            checkInput(!input.branchName.isNullOrBlank(), "branchName is required if branchId is not provided")
            val project = if (input.projectId != null) {
                checkInput(
                    input.projectName.isNullOrBlank(),
                    "Since projectId is provided, projectName is not required."
                )
                structureService.getProject(ID.of(input.projectId!!))
            } else {
                checkInput(
                    !input.projectName.isNullOrBlank(),
                    "When using branchName, projectName is required if projectId is not provided"
                )
                structureService.findProjectByName(input.projectName!!).getOrNull()
                    ?: throw ProjectNotFoundException(input.projectName)
            }
            structureService.findBranchByName(project.name, input.branchName!!).getOrNull()
                ?: throw BranchNotFoundException(project.name, input.branchName)
        }
    }

    private fun checkInput(
        ok: Boolean,
        message: String
    ) {
        if (!ok) {
            throw BuildInputMismatchException(message)
        }
    }

}

class BuildInputMismatchException(message: String) : InputException(message)

interface BuildInput {
    /**
     * Project ID (required unless project name is provided)
     */
    val projectId: Int?

    /**
     * Project Name (required unless project ID is provided)
     */
    val projectName: String?

    /**
     * Branch ID (required unless project & branch names are provided)
     */
    val branchId: Int?

    /**
     * Branch name (required together with project name, unless branch ID is provided)
     */
    val branchName: String?
}

data class CreateBuildInput(
    @APIDescription("Project ID (required unless project name is provided)")
    override val projectId: Int?,
    @APIDescription("Project Name (required unless project ID is provided)")
    override val projectName: String?,
    @APIDescription("Branch ID (required unless project & branch names are provided)")
    override val branchId: Int?,
    @APIDescription("Branch name (required together with project name, unless branch ID is provided)")
    override val branchName: String?,
    @get:NotNull(message = "The name is required.")
    @get:Pattern(regexp = NameDescription.NAME, message = "The name ${NameDescription.NAME_MESSAGE_SUFFIX}")
    @APIDescription("Build name")
    val name: String,
    @APIDescription("Build description")
    val description: String?,
    @APIDescription("Optional run info")
    @TypeRef
    val runInfo: RunInfoInput?,
) : BuildInput

data class UpdateBuildInput(
    @APIDescription("ID of the build to update")
    val id: Int,
    @APIDescription("Creation timestamp to update")
    val creation: LocalDateTime? = null,
    @APIDescription("Name for the build")
    val name: String? = null,
    @APIDescription("Description for the build")
    val description: String? = null,
)

data class CreateBuildOrGetInput(
    @APIDescription("Project ID (required unless project name is provided)")
    override val projectId: Int?,
    @APIDescription("Project Name (required unless project ID is provided)")
    override val projectName: String?,
    @APIDescription("Branch ID (required unless project & branch names are provided)")
    override val branchId: Int?,
    @APIDescription("Branch name (required together with project name, unless branch ID is provided)")
    override val branchName: String?,
    @get:NotNull(message = "The name is required.")
    @get:Pattern(regexp = NameDescription.NAME, message = "The name ${NameDescription.NAME_MESSAGE_SUFFIX}")
    @APIDescription("Build name")
    val name: String,
    @APIDescription("Build description")
    val description: String?,
    @APIDescription("Optional run info")
    @TypeRef
    val runInfo: RunInfoInput?,
) : BuildInput

data class LinkBuildInput(
    @APIDescription("Name of the project from which the link must be created")
    val fromProject: String,
    @APIDescription("Name of the build from which the link must be created")
    val fromBuild: String,
    @APIDescription("Name of the project to which the link must be created")
    val toProject: String,
    @APIDescription("Name of the build to which the link must be created")
    val toBuild: String,
    @APIDescription("Qualifier for the link")
    val qualifier: String?,
)

data class LinkBuildByIdInput(
    @APIDescription("ID of the build from which the link must be created")
    val fromBuild: Int,
    @APIDescription("ID of the build to which the link must be created")
    val toBuild: Int,
    @APIDescription("Qualifier for the link")
    val qualifier: String?,
)


data class LinksBuildInput(
    @APIDescription("Name of the project from which the link must be created")
    val fromProject: String,
    @APIDescription("Name of the build from which the link must be created")
    val fromBuild: String,
    @APIDescription("List of links to add")
    @ListRef
    val links: List<LinksBuildInputItem>
)

data class LinksBuildInputItem(
    @APIDescription("Name of the project to which the link must be created")
    val project: String,
    @APIDescription("Name of the build to which the link must be created")
    val build: String,
    @APIDescription("Qualifier for the link")
    val qualifier: String?,
)

data class DeleteBuildByIdInput(
    @APIDescription("ID of the build to delete")
    val id: Int,
)

data class DeleteBuildLinksInput(
    @APIDescription("ID of the build from which links must be deleted")
    val fromBuild: Int,
    @APIDescription("Name of the target project — if null, links to all projects are deleted")
    val project: String? = null,
    @APIDescription("Qualifier to match — if null, links with any qualifier are deleted")
    val qualifier: String? = null,
)

@Component
class GQLInputLinksBuildInputItem : SimpleGQLInputType<LinksBuildInputItem>(LinksBuildInputItem::class)
