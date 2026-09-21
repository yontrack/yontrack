package net.nemerosa.ontrack.extension.stash.client

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.stash.model.BitbucketProject
import net.nemerosa.ontrack.extension.stash.model.BitbucketRepository
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.scm.BitbucketServerPR
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.extension.support.client.jackson2RestTemplateBuilder
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException.NotFound
import org.springframework.web.client.RequestCallback
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject
import java.time.LocalDateTime


/**
 * Reference at https://docs.atlassian.com/bitbucket-server/rest/5.16.0/bitbucket-rest.html
 */
class BitbucketClientImpl(
    private val configuration: StashConfiguration,
    private val maxCommits: Int,
) : BitbucketClient {

    override val projects: List<BitbucketProject>
        get() = template.getForObject("/rest/api/1.0/projects?limit=1000", ProjectsResponse::class.java)?.values
            ?: emptyList()

    override fun getRepositories(project: BitbucketProject): List<BitbucketRepository> =
        template.getForObject(
            "/rest/api/1.0/projects/${project.key}/repos?limit=1000",
            RepositoriesResponse::class.java
        )
            ?.values
            ?.map {
                BitbucketRepository(
                    project = project.key,
                    repository = it.slug
                )
            }
            ?: emptyList()

    override fun getRepositoryLastModified(repo: BitbucketRepository): LocalDateTime? =
        template.getForObject(
            "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/last-modified?at=HEAD",
            RepositoryLastModifiedResponse::class.java
        )
            ?.latestCommit?.authorTimestamp
            ?.let { timestamp -> Time.from(timestamp) }

    override fun createBranch(repo: BitbucketRepository, source: String, target: String): String =
        template.postForObject(
            "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/branches", mapOf(
                "name" to target,
                "startPoint" to source,
            ), CreateBranchResponse::class.java
        )
            ?.latestCommit
            ?: throw BitbucketServerCannotCreateBranchException()

    override fun deleteBranch(repo: BitbucketRepository, branch: String) {
        // Cleaning pending PRs
        declinePendingPRs(repo, branch)
        // Deleting the branch
        val request = mapOf(
            "name" to "refs/heads/$branch"
        )
        val requestCallback: RequestCallback = template.httpEntityCallback<Any>(request, JsonNode::class.java)
        template.execute<Any>(
            "/rest/branch-utils/latest/projects/${repo.project}/repos/${repo.repository}/branches",
            HttpMethod.DELETE,
            requestCallback,
            null,
        )
    }

    private fun declinePendingPRs(repo: BitbucketRepository, branch: String) {
        // Get open pull requests from the branch
        val response = template.getForObject<PendingPRsResponse>(
            "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/pull-requests?state=OPEN&direction=OUTGOING&at=refs/heads/$branch"
        )!!
        // Declining all PRs
        response.values.forEach { pr ->
            template.postForObject<JsonNode>(
                "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/pull-requests/${pr.id}/decline",
                mapOf(
                    "version" to pr.version,
                )
            )!!
        }
    }

    override fun isBranchExisting(repo: BitbucketRepository, branch: String): Boolean {
        val response = template.getForObject<FindBranchesResponse>(
            "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/branches?filterText=${branch}"
        )!!
        return response.values.any { it.displayId == branch }
    }

    override fun getBranchesForCommit(
        repo: BitbucketRepository,
        commit: String
    ): List<String> {
        val response = template.getForObject<BranchInfoCommitResponse>(
            "/rest/branch-utils/1.0/projects/${repo.project}/repos/${repo.repository}/branches/info/${commit}"
        )!!
        return response.values.filter { it.type == "BRANCH" }.map { it.displayId }
    }

    override fun geBranchLastCommit(repo: BitbucketRepository, branch: String): String? {
        if (isBranchExisting(repo, branch)) {
            val response = template.getForObject<GetBranchLastCommitResponse>(
                "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/commits?limit=1&until=refs/heads/${branch}"
            )!!
            return response.values.firstOrNull()?.id
        } else {
            return null
        }
    }

    override fun download(repo: BitbucketRepository, branch: String?, path: String): ByteArray? =
        try {
            var uri = "/rest/api/1.0/projects/${repo.project}/repos/${repo.repository}/raw/$path"
            if (!branch.isNullOrBlank()) {
                uri += "?at=$branch"
            }
            template.getForObject<ByteArray>(uri)
        } catch (_: NotFound) {
            null
        }

    override fun upload(
        repo: BitbucketRepository,
        branch: String,
        commit: String,
        path: String,
        content: ByteArray,
        message: String
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.add("branch", branch)
        body.add("content", ByteArrayResource(content))
        body.add("message", message)
        if (commit.isNotBlank()) {
            body.add("sourceCommitId", commit)
        }

        val requestEntity: HttpEntity<MultiValueMap<String, Any>> = HttpEntity(body, headers)

        template.put(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/browse/$path",
            requestEntity
        )
    }

    override fun createPR(
        repo: BitbucketRepository,
        title: String,
        head: String,
        base: String,
        body: String,
        reviewers: List<String>,
    ): BitbucketServerPR =
        template.postForObject(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/pull-requests",
            mapOf(
                "title" to title,
                "description" to body,
                "fromRef" to mapOf(
                    "id" to "refs/heads/$head"
                ),
                "toRef" to mapOf(
                    "id" to "refs/heads/$base"
                ),
                "reviewers" to reviewers.map {
                    mapOf(
                        "user" to mapOf(
                            "name" to it,
                        )
                    )
                }
            ),
            BitbucketServerPR::class.java
        ) ?: throw BitbucketServerCannotCreatePRException()

    override fun approvePR(repo: BitbucketRepository, prId: Int, user: String, token: String) {
        tokenTemplate(token).put(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/pull-requests/${prId}/participants/${user}",
            mapOf("status" to "APPROVED")
        )
    }

    override fun getPR(
        repo: BitbucketRepository,
        prId: Int
    ): BitbucketServerPR? =
        try {
            template.getForObject<BitbucketServerPR>(
                "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/pull-requests/${prId}",
            )
        } catch (_: NotFound) {
            null
        }

    override fun isPRMergeable(repo: BitbucketRepository, prId: Int): Boolean {
        val response = template.getForObject<PRMergeableResponse>(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/pull-requests/${prId}/merge",
        )!!
        return response.outcome == "CLEAN"
                && response.vetoes.isNullOrEmpty()
                && !response.conflicted
    }

    override fun mergePR(repo: BitbucketRepository, prId: Int, message: String) {
        val pr = template.getForObject<PRResponse>(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/pull-requests/${prId}"
        )!!
        template.postForObject(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/pull-requests/${prId}/merge",
            mapOf(
                "message" to message,
                "version" to pr.version,
            ),
            JsonNode::class.java
        )
    }

    override fun getCommits(
        repo: BitbucketRepository,
        fromCommit: String,
        toCommit: String
    ): List<BitbucketServerCommit> =
        template.getForObject<JsonNode>(
            "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/commits?since=$fromCommit&until=$toCommit&limit=$maxCommits"
        )!!.path("values").map {
            it.parse<BitbucketServerCommit>()
        }

    override fun getCommit(
        repository: BitbucketRepository,
        commit: String
    ): BitbucketServerCommit? =
        try {
            template.getForObject<JsonNode>(
                "/rest/api/latest/projects/${repository.project}/repos/${repository.repository}/commits/${commit}"
            )!!.parse<BitbucketServerCommit>()
        } catch (_: NotFound) {
            null
        }

    override fun forEachCommit(repo: BitbucketRepository, code: (BitbucketServerCommit) -> Unit) {
        var start = 0
        var isLastPage = false
        while (!isLastPage) {
            val response = template.getForObject<JsonNode>(
                "/rest/api/latest/projects/${repo.project}/repos/${repo.repository}/commits?start=$start&limit=$maxCommits"
            )!!
            response.path("values").forEach {
                code(it.parse<BitbucketServerCommit>())
            }
            isLastPage = response.path("isLastPage").asBoolean(true)
            if (!isLastPage) {
                start = response.path("nextPageStart").asInt()
            }
        }
    }

    private val template = jackson2RestTemplateBuilder()
        .rootUri(configuration.url)
        .basicAuthentication(
            requireNotNull(configuration.user) { "Username must not be null" },
            requireNotNull(configuration.password) { "Password must not be null" }
        )
        .build()

    private fun tokenTemplate(token: String) = jackson2RestTemplateBuilder()
        .rootUri(configuration.url)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .build()

    private class BranchInfoCommitResponse(
        val values: List<BranchInfoCommit>,
    )

    private class BranchInfoCommit(
        val displayId: String,
        val type: String,
    )

    private class PRResponse(
        val id: Int,
        val version: Int,
    )

    private class PendingPRsResponse(
        val values: List<PRResponse>,
    )

    private class PRMergeableResponse(
        val outcome: String,
        val vetoes: List<Veto>?,
        val conflicted: Boolean,
    )

    private class Veto(
        val summaryMessage: String,
    )

    private class CreateBranchResponse(
        val latestCommit: String,
    )

    private class ProjectsResponse(
        val values: List<BitbucketProject>
    )

    private class RepositoriesResponse(
        val values: List<RepositoriesResponseItem>
    )

    private class RepositoriesResponseItem(
        @Suppress("unused") val id: Int,
        val slug: String
    )

    private class FindBranchesResponse(
        val values: List<FindBranchesItem>,
    )

    private class FindBranchesItem(
        val displayId: String,
    )

    private class RepositoryLastModifiedResponse(
        val latestCommit: CommitResponse?
    )

    private class GetBranchLastCommitResponse(
        val values: List<CommitResponse>,
    )

    private class CommitResponse(
        val id: String,
        val authorTimestamp: Long?
    )

    private class BitbucketServerCannotCreateBranchException : BaseException("Cannot create branch in Bitbucket Server")
    private class BitbucketServerCannotCreatePRException : BaseException("Cannot create PR in Bitbucket Server")

}