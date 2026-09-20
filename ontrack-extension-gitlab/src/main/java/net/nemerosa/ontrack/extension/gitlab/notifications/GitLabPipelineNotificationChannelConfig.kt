package net.nemerosa.ontrack.extension.gitlab.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList

data class GitLabPipelineNotificationChannelConfig(
    @APIDescription("Name of the GitLab configuration to use for the connection.")
    @APILabel("Configuration")
    val config: String,
    @APIDescription("Full path of the GitLab project, subgroups included, like `group/subgroup/project`. Templated.")
    @APILabel("Project")
    val project: String,
    @APIDescription("Branch or tag to run the pipeline on. Templated.")
    @APILabel("Ref")
    val ref: String,
    @APIDescription("Variables to pass to the pipeline. Their values are templated. They are not masked: secrets belong in the CI/CD variables of the GitLab project.")
    @APILabel("Variables")
    @DocumentationList
    val variables: List<GitLabPipelineNotificationChannelConfigVariable> = emptyList(),
    @APIDescription("""How to call the pipeline. ASYNC (the default) means that the pipeline is triggered in "fire and forget" mode. When set to SYNC, Yontrack waits for the completion of the pipeline, with a given timeout, and reports an error if the pipeline does not succeed.""")
    @APILabel("Call mode")
    val callMode: GitLabPipelineNotificationChannelConfigCallMode = GitLabPipelineNotificationChannelConfigCallMode.ASYNC,
    @APIDescription("Timeout in seconds, when waiting for the pipeline in SYNC mode")
    @APILabel("Timeout")
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}

data class GitLabPipelineNotificationChannelConfigVariable(
    @APIDescription("Name of the variable")
    val name: String,
    @APIDescription("Value of the variable. Templated.")
    val value: String,
)

/**
 * Defines how the pipeline is called.
 */
enum class GitLabPipelineNotificationChannelConfigCallMode {

    /**
     * Yontrack triggers the pipeline and returns immediately.
     */
    ASYNC,

    /**
     * Yontrack triggers the pipeline and waits for its completion.
     */
    SYNC,

}
