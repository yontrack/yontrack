package net.nemerosa.ontrack.extension.git.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build

/**
 * Defines the way to link builds to Git commits, in order to manage the change logs, the Git searches
 * and synchronisations.
 *
 * @param <T> Type of configuration data
 */
interface BuildGitCommitLink<T> {

    @APIDescription("ID of the link")
    val id: String

    @APIDescription("Display name for the link")
    val name: String

    /**
     * Clones the configuration.
     */
    fun clone(data: T, replacementFunction: (String) -> String): T

    /**
     * For the given `build`, returns the corresponding Git commit
     *
     * @param build Build to get the commit for
     * @param data  Configuration of the link
     * @return Committish (short or long SHA, tag, head, etc.)
     */
    fun getCommitFromBuild(build: Build, data: T): String

    /**
     * Parses the configuration from a JSON node
     */
    fun parseData(node: JsonNode?): T

    /**
     * Formats the configuration data as JSON
     */
    fun toJson(data: T): JsonNode

    /**
     * Gets the earliest build after a given commit on a branch.
     *
     * @param branch Branch to look the build into
     * @param gitClient Preconfigured Git client
     * @param branchConfiguration Git configuration of the branch
     * @param data Configuration data for this link
     * @param commit The commit to look for
     * @return ID of the build or `null` if not found
     */
    fun getEarliestBuildAfterCommit(branch: Branch, gitClient: GitRepositoryClient, branchConfiguration: GitBranchConfiguration, data: T, commit: String): Int?

    /**
     * Checks if a build name is valid for this configuration.
     */
    fun isBuildNameValid(name: String, data: T): Boolean
}
