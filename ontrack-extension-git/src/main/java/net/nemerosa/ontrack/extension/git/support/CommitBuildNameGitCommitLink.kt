package net.nemerosa.ontrack.extension.git.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.model.BuildGitCommitLink
import net.nemerosa.ontrack.extension.git.model.GitBranchConfiguration
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getBooleanField
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import java.util.regex.Pattern
import kotlin.streams.asSequence

@Component
class CommitBuildNameGitCommitLink(
    private val structureService: StructureService
) : BuildGitCommitLink<CommitLinkConfig> {

    private val abbreviatedPattern = Pattern.compile("[0-9a-f]{7}")
    private val fullPattern = Pattern.compile("[0-9a-f]{40}")

    override val id: String = "commit"
    override val name: String = "Commit as name"

    override fun clone(data: CommitLinkConfig, replacementFunction: (String) -> String): CommitLinkConfig {
        return data
    }

    override fun getCommitFromBuild(build: Build, data: CommitLinkConfig): String {
        return build.name
    }

    override fun parseData(node: JsonNode?): CommitLinkConfig =
        if (node != null) {
            CommitLinkConfig(
                node.getBooleanField("abbreviated") ?: true
            )
        } else {
            CommitLinkConfig(true)
        }

    override fun toJson(data: CommitLinkConfig): JsonNode =
        mapOf("abbreviated" to data.abbreviated).asJson()

    override fun getEarliestBuildAfterCommit(
        branch: Branch,
        gitClient: GitRepositoryClient,
        branchConfiguration: GitBranchConfiguration,
        data: CommitLinkConfig,
        commit: String
    ): Int? {
        return if (gitClient.isCommit(commit)) {
            gitClient.log(
                String.format("%s~1", commit),
                gitClient.getBranchRef(branchConfiguration.branch)
            ).asSequence()
                .sorted()
                .map { gitCommit ->
                    if (data.abbreviated)
                        gitCommit.shortId
                    else
                        gitCommit.id
                }
                .mapNotNull { name ->
                    structureService.findBuildByName(branch.project.name, branch.name, name).orElse(null)
                }
                .sortedBy { it.id() }
                .firstOrNull()
                ?.id()
        } else {
            null
        }
    }

    override fun isBuildNameValid(name: String, data: CommitLinkConfig): Boolean {
        return if (data.abbreviated) {
            abbreviatedPattern.matcher(name).matches()
        } else {
            fullPattern.matcher(name).matches()
        }
    }

}
