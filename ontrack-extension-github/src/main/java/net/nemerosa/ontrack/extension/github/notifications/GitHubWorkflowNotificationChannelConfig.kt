package net.nemerosa.ontrack.extension.github.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList

data class GitHubWorkflowNotificationChannelConfig(
    @APIDescription("Name of the GitHub configuration to use for the connection.")
    @APILabel("Configuration")
    val config: String,
    @APIDescription("Repository owner for the workflow")
    @APILabel("Owner")
    val owner: String,
    @APIDescription("Repository name for the workflow")
    @APILabel("Repository")
    val repository: String,
    @APIDescription("Workflow ID (like main.yml)")
    @APILabel("Workflow ID")
    val workflowId: String,
    @APIDescription("Git reference (branch, tag or commit SHA) for the workflow")
    @APILabel("Reference")
    val reference: String,
    @APIDescription("Inputs to send to to the workflow dispatch")
    @APILabel("Inputs")
    @DocumentationList
    val inputs: List<GitHubWorkflowNotificationChannelConfigInput> = emptyList(),
    @APIDescription("""How to call the Workflow. ASYNC (the default) means that the job is called in "fire and forget" mode. When set to SYNC, Yontrack will wait for the completion of the job to success, with a given timeout (not recommended).""")
    @APILabel("Call mode")
    val callMode: GitHubWorkflowNotificationChannelConfigCallMode = GitHubWorkflowNotificationChannelConfigCallMode.ASYNC,
    @APIDescription("Timeout in seconds")
    @APILabel("Timeout")
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    @APIDescription("If defined, overrides the GitHub configuration for passing the `id` input to the workflow, used to find its run when GitHub does not return it")
    @APILabel("Send ID")
    val sendId: Boolean? = null,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}
