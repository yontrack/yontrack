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
import org.springframework.web.client.RestClientResponseException
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
         * Merge responses meaning "not now": merge checks failing (400), a ref changed during the merge (409),
         * a merge timing out on the Bitbucket side (555).
         */
        private val NOT_MERGEABLE_STATUSES = setOf(400, 409, 555)

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

    override fun resolveReviewers(workspace: String, reviewers: List<String>): List<String> {
        if (reviewers.all { it.isUuid() }) {
            return reviewers
        }
        val members = followPages(
            UriComponentsBuilder.fromUriString(ROOT_URI)
                .path("/2.0/workspaces/$workspace/members")
                .queryParam("pagelen", MAX_PAGE_LENGTH)
                .build().encode().toUri(),
            BitbucketCloudWorkspaceMemberList::class,
        ).mapNotNull { it.user }
        return reviewers.map { reviewer ->
            if (reviewer.isUuid()) {
                reviewer
            } else {
                members.firstOrNull { it.account_id == reviewer }?.uuid
                    ?: members.firstOrNull { it.nickname == reviewer }?.uuid
                    ?: members.firstOrNull { it.display_name == reviewer }?.uuid
                    ?: throw BitbucketCloudReviewerNotFoundException(workspace, reviewer)
            }
        }
    }

    private fun String.isUuid() = startsWith("{") && endsWith("}")

    override fun createPullRequest(
        workspace: String,
        repository: String,
        from: String,
        to: String,
        title: String,
        description: String,
        reviewers: List<String>,
    ): BitbucketCloudPullRequest =
        template.postForObject(
            repositoryUri(workspace, repository, "pullrequests"),
            mapOf(
                "title" to title,
                "description" to description,
                "source" to mapOf("branch" to mapOf("name" to from)),
                "destination" to mapOf("branch" to mapOf("name" to to)),
                "reviewers" to reviewers.map { mapOf("uuid" to it) },
            ),
            BitbucketCloudPullRequest::class.java
        ) ?: throw BitbucketCloudNoResponseException("pullrequests")

    override fun approvePullRequest(workspace: String, repository: String, id: Int) {
        template.postForEntity(
            repositoryUri(workspace, repository, "pullrequests/$id/approve"),
            null,
            Void::class.java
        )
    }

    override fun getPullRequestStatuses(workspace: String, repository: String, id: Int): List<String> =
        followPages(
            repositoryUri(workspace, repository, "pullrequests/$id/statuses", "pagelen" to MAX_PAGE_LENGTH),
            BitbucketCloudCommitStatusList::class,
        ).mapNotNull { it.state }

    override fun mergePullRequest(
        workspace: String,
        repository: String,
        id: Int,
        strategy: String,
        message: String,
        closeSourceBranch: Boolean,
    ): BitbucketCloudMergeOutcome =
        try {
            val response = template.postForEntity(
                repositoryUri(workspace, repository, "pullrequests/$id/merge"),
                mapOf(
                    "type" to "pullrequest_merge_parameters",
                    "message" to message,
                    "close_source_branch" to closeSourceBranch,
                    "merge_strategy" to strategy,
                ),
                Void::class.java
            )
            if (response.statusCode.value() == 202) {
                BitbucketCloudMergeOutcome.PENDING
            } else {
                BitbucketCloudMergeOutcome.MERGED
            }
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() in NOT_MERGEABLE_STATUSES) {
                BitbucketCloudMergeOutcome.NOT_MERGEABLE
            } else {
                throw e
            }
        }

    /**
     * Items of all the pages, following the absolute `next` links.
     */
    private fun <T, P : BitbucketCloudPaginatedList<T>> followPages(first: URI, pageType: KClass<P>): List<T> {
        val results = mutableListOf<T>()
        var next: URI? = first
        while (next != null) {
            val page = template.getForObject(next, pageType.java) ?: break
            results += page.values
            next = page.next?.takeIf { it.isNotBlank() }?.let { URI.create(it) }
        }
        return results
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
