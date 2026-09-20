package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * Default values for the [GitLab post-processing][GitLabPostProcessing].
 *
 * There is no "pipeline" here, as there is on Bitbucket Cloud: GitLab has no named custom pipeline. What runs
 * is the `.gitlab-ci.yml` of the project on the [ref], which selects its post-processing job with a `rules:`
 * clause on the variables Yontrack sends.
 */
class GitLabPostProcessingSettings(
    @APILabel("Configuration")
    @APIDescription("Default GitLab configuration to use for the connection")
    val config: String?,
    @APILabel("Project")
    @APIDescription("Default full path of the GitLab project containing the pipeline, like `group/subgroup/project`")
    val project: String?,
    @APILabel("Ref")
    @APIDescription("Branch or tag to run the pipeline on")
    val ref: String = DEFAULT_REF,
    @APILabel("Retries")
    @APIDescription("The amount of times we check for the completion of the post-processing pipeline")
    val retries: Int = DEFAULT_RETRIES,
    @APILabel("Retry interval")
    @APIDescription("The time (in seconds) between two checks for the completion of the post-processing pipeline, never less than 10 seconds")
    val retriesDelaySeconds: Int = DEFAULT_RETRIES_DELAY_SECONDS,
) {
    companion object {
        const val DEFAULT_REF = "main"
        const val DEFAULT_RETRIES = 10
        const val DEFAULT_RETRIES_DELAY_SECONDS = 30
    }
}
