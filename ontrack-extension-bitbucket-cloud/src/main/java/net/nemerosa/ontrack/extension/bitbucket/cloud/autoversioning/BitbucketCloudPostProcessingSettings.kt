package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

class BitbucketCloudPostProcessingSettings(
    @APILabel("Configuration")
    @APIDescription("Default Bitbucket Cloud configuration to use for the connection")
    val config: String?,
    @APILabel("Workspace")
    @APIDescription("Default workspace of the repository containing the pipeline")
    val workspace: String?,
    @APILabel("Repository")
    @APIDescription("Default repository containing the pipeline")
    val repository: String?,
    @APILabel("Pipeline")
    @APIDescription("Name of the custom pipeline containing the post-processing (like `yontrack-auto-versioning`)")
    val pipeline: String?,
    @APILabel("Branch")
    @APIDescription("Branch to run the pipeline on")
    val branch: String = DEFAULT_BRANCH,
    @APILabel("Retries")
    @APIDescription("The amount of times we check for the completion of the post-processing pipeline")
    val retries: Int = DEFAULT_RETRIES,
    @APILabel("Retry interval")
    @APIDescription("The time (in seconds) between two checks for the completion of the post-processing pipeline, never less than 10 seconds")
    val retriesDelaySeconds: Int = DEFAULT_RETRIES_DELAY_SECONDS,
) {
    companion object {
        const val DEFAULT_BRANCH = "main"
        const val DEFAULT_RETRIES = 10
        const val DEFAULT_RETRIES_DELAY_SECONDS = 30
    }
}
