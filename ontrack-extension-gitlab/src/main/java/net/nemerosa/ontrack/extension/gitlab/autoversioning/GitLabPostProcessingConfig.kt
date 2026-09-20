package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Configuration of the GitLab post-processing for an auto-versioning order.
 *
 * Every field is optional: the connection fields fall back to [GitLabPostProcessingSettings].
 */
data class GitLabPostProcessingConfig(
    @APIDescription("This image defines the environment for the upgrade command to run in, passed as `DOCKER_IMAGE`")
    val dockerImage: String? = null,
    @APIDescription("Command to run in the Docker container, passed as `DOCKER_COMMAND`")
    val dockerCommand: String? = null,
    @APIDescription("Commit message to use to commit and push the result of the post-processing, passed as `COMMIT_MESSAGE`")
    val commitMessage: String? = null,
    @APIDescription("GitLab configuration to use for the connection, to override the default settings")
    val config: String? = null,
    @APIDescription("Full path of the GitLab project containing the pipeline, to override the default settings")
    val project: String? = null,
    @APIDescription("Branch or tag to run the pipeline on, to override the default settings")
    val ref: String? = null,
)
