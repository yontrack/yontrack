package net.nemerosa.ontrack.extension.bitbucket.cloud

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * [BitbucketCloudTestRepositoryApi] over the Bitbucket Cloud REST API.
 */
class BitbucketCloudTestRestApi(
    private val template: RestTemplate,
    private val root: String = ROOT,
    private val workspace: String,
    private val repository: String,
) : BitbucketCloudTestRepositoryApi {

    private fun uri(vararg segments: String, query: Map<String, Any> = emptyMap()): URI =
        UriComponentsBuilder.fromUriString(root)
            .pathSegment("2.0", "repositories", workspace, repository, *segments)
            .apply { query.forEach { (name, value) -> queryParam(name, value) } }
            .build()
            .encode()
            .toUri()

    override fun branches(prefix: String): List<String> =
        paginate(uri("refs", "branches", query = mapOf("q" to "name ~ \"$prefix\"", "pagelen" to 100))) {
            it.path("name").asText()
        }

    override fun openPullRequests(): List<BitbucketCloudTestPullRequest> =
        paginate(uri("pullrequests", query = mapOf("state" to "OPEN", "pagelen" to 50))) {
            BitbucketCloudTestPullRequest(
                id = it.path("id").asInt(),
                sourceBranch = it.path("source").path("branch").path("name").asText(),
            )
        }

    override fun declinePullRequest(id: Int) {
        template.postForObject(uri("pullrequests", id.toString(), "decline"), null, JsonNode::class.java)
    }

    override fun deleteBranch(name: String) {
        template.delete(uri("refs", "branches", name))
    }

    private fun <T> paginate(first: URI, map: (JsonNode) -> T): List<T> {
        val results = mutableListOf<T>()
        var next: URI? = first
        while (next != null) {
            val page = template.getForObject(next, JsonNode::class.java) ?: break
            page.path("values").mapTo(results, map)
            next = page.path("next").takeIf { it.isTextual }?.let { URI.create(it.asText()) }
        }
        return results
    }

    companion object {
        const val ROOT = "https://api.bitbucket.org"

        fun of(env: BitbucketCloudTestEnv, identity: BitbucketCloudTestIdentity = env.bot) =
            BitbucketCloudTestRestApi(
                template = restTemplateBuilder().basicAuthentication(identity.email, identity.token).build(),
                workspace = env.workspace,
                repository = env.repository,
            )
    }
}
