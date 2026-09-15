package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.*
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KClass

class DefaultBitbucketCloudClient(
    private val configuration: BitbucketCloudConfiguration,
) : BitbucketCloudClient {

    companion object {
        const val ROOT_URI = "https://api.bitbucket.org"

        /**
         * Maximum page length accepted by the Bitbucket Cloud commits endpoint.
         */
        const val MAX_PAGE_LENGTH = 100

        /**
         * `Authorization` header for a configuration: HTTP basic with the email for an API token,
         * Bearer for an access token.
         */
        fun authorizationHeader(configuration: BitbucketCloudConfiguration): String {
            val token = configuration.token?.takeIf { it.isNotBlank() }
                ?: error("Bitbucket Cloud configuration ${configuration.name} has no token.")
            return when (configuration.authType) {
                BitbucketCloudAuthType.API_TOKEN -> {
                    val email = configuration.email?.takeIf { it.isNotBlank() }
                        ?: error("Bitbucket Cloud configuration ${configuration.name} has no email for its API token.")
                    "Basic " + Base64.getEncoder().encodeToString("$email:$token".toByteArray(Charsets.UTF_8))
                }

                BitbucketCloudAuthType.ACCESS_TOKEN -> "Bearer $token"
            }
        }
    }

    override fun validate() {
        when (configuration.authType) {
            BitbucketCloudAuthType.API_TOKEN -> get<JsonNode>("/2.0/user")
            BitbucketCloudAuthType.ACCESS_TOKEN -> get<JsonNode>("/2.0/hook_events")
        }
    }

    override fun getRepositories(workspace: String): List<BitbucketCloudRepository> =
        paginate<BitbucketCloudRepository, BitbucketCloudRepositoryList> { page ->
            "/2.0/repositories/$workspace?page=$page"
        }

    override fun getRepositoryLastModified(repository: BitbucketCloudRepository): LocalDateTime? =
        LocalDateTime.parse(repository.updated_on, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    override fun getRepositoryCreationDate(repository: BitbucketCloudRepository): LocalDateTime? =
        LocalDateTime.parse(repository.created_on, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    override fun getRepository(workspace: String, repository: String): BitbucketCloudRepository =
        get("/2.0/repositories/$workspace/$repository")

    override fun getBranchLastCommit(workspace: String, repository: String, branch: String): String? =
        notFoundAsNull {
            template.getForObject(
                repositoryUri(workspace, repository, "refs/branches/$branch"),
                BitbucketCloudBranch::class.java
            )?.target?.hash
        }

    override fun createBranch(workspace: String, repository: String, sourceBranch: String, newBranch: String): String {
        val hash = getBranchLastCommit(workspace, repository, sourceBranch)
            ?: throw BitbucketCloudBranchNotFoundException(workspace, repository, sourceBranch)
        val created = template.postForObject(
            repositoryUri(workspace, repository, "refs/branches"),
            mapOf(
                "name" to newBranch,
                "target" to mapOf("hash" to hash),
            ),
            BitbucketCloudBranch::class.java
        )
        return created?.target?.hash ?: hash
    }

    override fun deleteBranch(workspace: String, repository: String, branch: String) {
        notFoundAsNull {
            template.delete(repositoryUri(workspace, repository, "refs/branches/$branch"))
        }
    }

    override fun download(workspace: String, repository: String, ref: String, path: String): ByteArray? =
        notFoundAsNull {
            template.getForObject(
                repositoryUri(workspace, repository, "src/$ref/${path.trimStart('/')}"),
                ByteArray::class.java
            )
        }

    override fun upload(
        workspace: String,
        repository: String,
        branch: String,
        path: String,
        content: ByteArray,
        message: String,
    ) {
        val filePath = path.trimStart('/')
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("message", message)
            add("branch", branch)
            add(filePath, object : ByteArrayResource(content) {
                override fun getFilename(): String = filePath.substringAfterLast('/')
            })
        }
        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        template.postForEntity(
            repositoryUri(workspace, repository, "src"),
            HttpEntity(body, headers),
            Void::class.java
        )
    }

    override fun getCommits(
        workspace: String,
        repository: String,
        fromCommit: String,
        toCommit: String,
        maxCommits: Int,
    ): List<BitbucketCloudCommit> {
        val results = mutableListOf<BitbucketCloudCommit>()
        var next: URI? = repositoryUri(
            workspace, repository, "commits",
            "include" to toCommit,
            "exclude" to fromCommit,
            "pagelen" to minOf(MAX_PAGE_LENGTH, maxCommits).coerceAtLeast(1),
        )
        while (next != null && results.size < maxCommits) {
            val page = template.getForObject(next, BitbucketCloudCommitList::class.java) ?: break
            results += page.values.take(maxCommits - results.size)
            next = page.next?.takeIf { it.isNotBlank() }?.let { URI.create(it) }
        }
        return results
    }

    override fun getCommit(workspace: String, repository: String, commit: String): BitbucketCloudCommit? =
        notFoundAsNull {
            template.getForObject(
                repositoryUri(workspace, repository, "commit/$commit"),
                BitbucketCloudCommit::class.java
            )
        }

    override fun getPullRequest(workspace: String, repository: String, id: Int): BitbucketCloudPullRequest? =
        notFoundAsNull {
            template.getForObject(
                repositoryUri(workspace, repository, "pullrequests/$id"),
                BitbucketCloudPullRequest::class.java
            )
        }

    /**
     * URI to a resource of a repository. The [path] keeps its slashes (branch names, file paths), only characters
     * illegal in a path are encoded.
     */
    private fun repositoryUri(
        workspace: String,
        repository: String,
        path: String,
        vararg query: Pair<String, Any>,
    ): URI =
        UriComponentsBuilder.fromUriString(ROOT_URI)
            .path("/2.0/repositories/$workspace/$repository/$path")
            .apply { query.forEach { (name, value) -> queryParam(name, value) } }
            .build()
            .encode()
            .toUri()

    private fun <T> notFoundAsNull(code: () -> T): T? =
        try {
            code()
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }

    private inline fun <reified T, reified P : BitbucketCloudPaginatedList<T>> paginate(
        noinline path: (Int) -> String,
    ): List<T> = paginate(P::class, path)

    private fun <T, P : BitbucketCloudPaginatedList<T>> paginate(
        pageType: KClass<P>,
        path: (Int) -> String,
    ): List<T> {
        var page = 1
        val results = mutableListOf<T>()
        do {
            val list = get(pageType, path(page))
            results.addAll(list.values)
            page++
        } while (list.next != null)
        return results
    }

    private inline fun <reified T : Any> get(path: String): T =
        get(T::class, path)

    private fun <T : Any> get(responseType: KClass<T>, path: String): T =
        template.getForObject(path, responseType.java) ?: throw BitbucketCloudNoResponseException(path)

    internal val template: RestTemplate by lazy {
        RestTemplateBuilder()
            .rootUri(ROOT_URI)
            .defaultHeader(HttpHeaders.AUTHORIZATION, authorizationHeader(configuration))
            .build()
    }
}
