package net.nemerosa.ontrack.kdsl.spec.extension.gitlab

/**
 * Configuration of a subscription with the `gitlab-pipeline` channel (or `mock-gitlab-pipeline`), to pass as
 * `channelConfig`.
 *
 * @property config Name of the GitLab configuration
 * @property project Full path of the GitLab project, templated
 * @property ref Branch or tag to run the pipeline on, templated
 * @property variables Pipeline variables, values templated
 * @property callMode `ASYNC` or `SYNC`
 * @property timeoutSeconds Timeout when waiting in `SYNC` mode
 */
data class GitLabPipelineNotificationChannelConfig(
    val config: String,
    val project: String,
    val ref: String,
    val variables: List<Variable> = emptyList(),
    val callMode: String = ASYNC,
    val timeoutSeconds: Int = 30,
) {
    data class Variable(
        val name: String,
        val value: String,
    )

    companion object {
        const val CHANNEL = "gitlab-pipeline"
        const val MOCK_CHANNEL = "mock-gitlab-pipeline"
        const val ASYNC = "ASYNC"
        const val SYNC = "SYNC"
    }
}
