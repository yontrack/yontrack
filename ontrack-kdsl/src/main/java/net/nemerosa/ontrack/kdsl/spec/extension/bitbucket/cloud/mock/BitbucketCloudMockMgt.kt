package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.mock

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketCloudMgt
import java.net.URLEncoder

/**
 * Access to the pipelines recorded by the `mock-bitbucket-pipelines` channel (DEV profile only).
 */
class BitbucketCloudMockMgt(connector: Connector) : Connected(connector) {

    /**
     * Pipeline runs recorded for a configuration and a repository, oldest first.
     */
    fun pipelineRuns(config: String, workspace: String, repository: String): List<MockBitbucketPipelineRun> =
        connector.get(
            "/extension/bitbucket-cloud/mock/pipelines?config=${enc(config)}&workspace=${enc(workspace)}&repository=${enc(repository)}"
        ).body.asJson().map {
            // Element by element: a reified List<T> loses T and yields maps
            it.parse<MockBitbucketPipelineRun>()
        }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
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
