package net.nemerosa.ontrack.extension.git.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.model.GitBranchConfiguration
import net.nemerosa.ontrack.extension.git.model.IndexableBuildGitCommitLink
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.support.NoConfig
import org.springframework.stereotype.Component

@Component
class TagBuildNameGitCommitLink(
    private val structureService: StructureService
) : IndexableBuildGitCommitLink<NoConfig> {

    override val id: String = "tag"
    override val name: String = "Tag as name"

    override fun clone(data: NoConfig, replacementFunction: (String) -> String): NoConfig {
        return data
    }

    override fun getCommitFromBuild(build: Build, data: NoConfig): String {
        return build.name
    }

    override fun parseData(node: JsonNode?): NoConfig {
        return NoConfig.INSTANCE
    }

    override fun toJson(data: NoConfig): JsonNode =
        emptyMap<String, String>().asJson()

    override fun getEarliestBuildAfterCommit(
        branch: Branch,
        gitClient: GitRepositoryClient,
        branchConfiguration: GitBranchConfiguration,
        data: NoConfig,
        commit: String
    ): Int? {
        return gitClient.getTagsWhichContainCommit(commit)
            // ... gets the builds
            .mapNotNull { buildName ->
                structureService.findBuildByName(branch.project.name, branch.name, buildName).orElse(null)
            }
            // Get the minimum
            .minByOrNull { it.id() }
            // ... and its ID
            ?.id()
    }

    override fun getBuildNameFromTagName(tagName: String, data: NoConfig): String? {
        return tagName
    }

    override fun isBuildNameValid(name: String, data: NoConfig): Boolean {
        return true
    }

}
