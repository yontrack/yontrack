package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud

/**
 * Configuration of a subscription with the `bitbucket-pipelines` channel (or `mock-bitbucket-pipelines`),
 * to pass as `channelConfig`.
 *
 * @property config Name of the Bitbucket Cloud configuration
 * @property workspace Workspace slug, templated
 * @property repository Repository slug, templated
 * @property branch Branch to run the pipeline on, templated
 * @property pipeline Name of a custom pipeline, `null` for the default pipeline of the branch, templated
 * @property variables Non-secured variables, values templated
 * @property callMode `ASYNC` or `SYNC`
 * @property timeoutSeconds Timeout when waiting in `SYNC` mode
 */
data class BitbucketPipelinesNotificationChannelConfig(
    val config: String,
    val workspace: String,
    val repository: String,
    val branch: String,
    val pipeline: String? = null,
    val variables: List<Variable> = emptyList(),
    val callMode: String = ASYNC,
    val timeoutSeconds: Int = 30,
) {
    data class Variable(
        val name: String,
        val value: String,
    )

    companion object {
        const val CHANNEL = "bitbucket-pipelines"
        const val MOCK_CHANNEL = "mock-bitbucket-pipelines"
        const val ASYNC = "ASYNC"
        const val SYNC = "SYNC"
    }
}
