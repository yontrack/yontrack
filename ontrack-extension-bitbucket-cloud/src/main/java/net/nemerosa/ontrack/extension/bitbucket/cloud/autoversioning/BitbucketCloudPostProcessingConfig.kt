package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Configuration of the Bitbucket Cloud post-processing for an auto-versioning order.
 *
 * Every field is optional: the connection fields fall back to [BitbucketCloudPostProcessingSettings].
 */
data class BitbucketCloudPostProcessingConfig(
    @APIDescription("This image defines the environment for the upgrade command to run in, passed as `DOCKER_IMAGE`")
    val dockerImage: String? = null,
    @APIDescription("Command to run in the Docker container, passed as `DOCKER_COMMAND`")
    val dockerCommand: String? = null,
    @APIDescription("Commit message to use to commit and push the result of the post-processing, passed as `COMMIT_MESSAGE`")
    val commitMessage: String? = null,
    @APIDescription("Bitbucket Cloud configuration to use for the connection, to override the default settings")
    val config: String? = null,
    @APIDescription("Workspace of the repository containing the pipeline, to override the default settings")
    val workspace: String? = null,
    @APIDescription("Repository containing the pipeline, to override the default settings")
    val repository: String? = null,
    @APIDescription("Name of the custom pipeline to run, to override the default settings")
    val pipeline: String? = null,
    @APIDescription("Branch to run the pipeline on, to override the default settings")
    val branch: String? = null,
)
