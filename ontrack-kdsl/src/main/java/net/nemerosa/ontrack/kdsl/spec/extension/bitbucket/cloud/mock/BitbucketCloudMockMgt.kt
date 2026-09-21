package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.mock

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketCloudMgt

/**
 * Access to the pipelines recorded by the `mock-bitbucket-pipelines` channel (DEV profile only).
 */
class BitbucketCloudMockMgt(connector: Connector) : Connected(connector) {

    /**
     * Pipeline runs recorded for a configuration and a repository, oldest first.
     *
     * The parameters go through the connector's own `query` map rather than into the path. Percent-encoding
     * them here would be encoded a second time by the underlying `RestTemplate`, which leaves the server
     * reading a literal `%2F` instead of a `/`.
     */
    fun pipelineRuns(config: String, workspace: String, repository: String): List<MockBitbucketPipelineRun> =
        connector.get(
            path = "/extension/bitbucket-cloud/mock/pipelines",
            query = mapOf(
                "config" to config,
                "workspace" to workspace,
                "repository" to repository,
            ),
        ).body.asJson().values().map {
            // Element by element: a reified List<T> loses T and yields maps
            it.parse<MockBitbucketPipelineRun>()
        }
}

val BitbucketCloudMgt.mock: BitbucketCloudMockMgt get() = BitbucketCloudMockMgt(connector)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MockBitbucketPipelineRun(
    val uuid: String,
    val buildNumber: Int,
    val branch: String,
    val pipeline: String?,
    val variables: Map<String, String>,
)
