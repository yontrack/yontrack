package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Link between a build and the Bitbucket Pipelines run which created it.
 */
data class BuildBitbucketPipelineRunProperty(
    @APIDescription("Slug of the Bitbucket Cloud workspace")
    val workspace: String,
    @APIDescription("Slug of the repository in the workspace")
    val repository: String,
    @APIDescription("Number of the pipeline run")
    val buildNumber: Int,
    @APIDescription("UUID of the pipeline run")
    val uuid: String?,
    @APIDescription("Link to the pipeline run")
    val url: String,
) {
    companion object {
        /**
         * URL of a pipeline run in Bitbucket Cloud.
         */
        fun runUrl(workspace: String, repository: String, buildNumber: Int) =
            "https://bitbucket.org/$workspace/$repository/pipelines/results/$buildNumber"
    }
}
