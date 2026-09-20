package net.nemerosa.ontrack.kdsl.spec.extension.gitlab.mock

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabMgt

/**
 * Access to the pipelines recorded by the `mock-gitlab-pipeline` channel (DEV profile only).
 */
class GitLabMockMgt(connector: Connector) : Connected(connector) {

    /**
     * Pipeline runs recorded for a configuration and a project, oldest first.
     *
     * The parameters go through the connector's own `query` map rather than into the path. A GitLab project
     * is a **path** - `group/subgroup/project` - and percent-encoding it here would be encoded a second time
     * by the underlying `RestTemplate`, which leaves the server reading a literal `%2F`.
     */
    fun pipelineRuns(config: String, project: String): List<MockGitLabPipelineRun> =
        connector.get(
            path = "/extension/gitlab/mock/pipelines",
            query = mapOf(
                "config" to config,
                "project" to project,
            ),
        ).body.asJson().map {
            // Element by element: a reified List<T> loses T and yields maps
            it.parse<MockGitLabPipelineRun>()
        }
}

val GitLabMgt.mock: GitLabMockMgt get() = GitLabMockMgt(connector)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MockGitLabPipelineRun(
    val id: Long,
    val iid: Long,
    val project: String,
    val ref: String,
    val variables: Map<String, String>,
)
