package net.nemerosa.ontrack.extension.bitbucket.cloud.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList

data class BitbucketPipelinesNotificationChannelConfig(
    @APIDescription("Name of the Bitbucket Cloud configuration to use for the connection.")
    @APILabel("Configuration")
    val config: String,
    @APIDescription("Slug of the Bitbucket Cloud workspace. Templated.")
    @APILabel("Workspace")
    val workspace: String,
    @APIDescription("Slug of the repository. Templated.")
    @APILabel("Repository")
    val repository: String,
    @APIDescription("Branch to run the pipeline on. Templated.")
    @APILabel("Branch")
    val branch: String,
    @APIDescription("Name of a custom pipeline (as defined under `pipelines.custom` in `bitbucket-pipelines.yml`). Empty to run the default pipeline of the branch. Templated.")
    @APILabel("Pipeline")
    val pipeline: String? = null,
    @APIDescription("Variables to pass to the pipeline, as non-secured variables. Their values are templated. Secrets belong in the repository variables.")
    @APILabel("Variables")
    @DocumentationList
    val variables: List<BitbucketPipelinesNotificationChannelConfigVariable> = emptyList(),
    @APIDescription("""How to call the pipeline. ASYNC (the default) means that the pipeline is triggered in "fire and forget" mode. When set to SYNC, Yontrack waits for the completion of the pipeline, with a given timeout, and reports an error if the pipeline does not succeed.""")
    @APILabel("Call mode")
    val callMode: BitbucketPipelinesNotificationChannelConfigCallMode = BitbucketPipelinesNotificationChannelConfigCallMode.ASYNC,
    @APIDescription("Timeout in seconds, when waiting for the pipeline in SYNC mode")
    @APILabel("Timeout")
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30
    }
}

data class BitbucketPipelinesNotificationChannelConfigVariable(
    @APIDescription("Name of the variable")
    val name: String,
    @APIDescription("Value of the variable. Templated.")
    val value: String,
)

/**
 * Defines how the pipeline is called.
 */
enum class BitbucketPipelinesNotificationChannelConfigCallMode {

    /**
     * Yontrack triggers the pipeline and returns immediately.
     */
    ASYNC,

    /**
     * Yontrack triggers the pipeline and waits for its completion.
     */
    SYNC,

}
