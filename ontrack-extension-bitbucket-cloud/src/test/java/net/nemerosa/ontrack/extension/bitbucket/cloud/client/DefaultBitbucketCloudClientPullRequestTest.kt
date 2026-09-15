package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudMergeOutcome
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudReviewerNotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.*
import kotlin.test.assertEquals

class DefaultBitbucketCloudClientPullRequestTest {

    private val client = DefaultBitbucketCloudClient(
        BitbucketCloudConfiguration(
            name = "bbc",
            authType = BitbucketCloudAuthType.API_TOKEN,
            email = "bot@example.com",
            token = "secret",
        )
    )

    private val server = MockRestServiceServer.bindTo(client.template).build()

    private val repo = "https://api.bitbucket.org/2.0/repositories/ws/repo"

    private val pr12 = """
        {
            "id": 12,
            "title": "Upgrade",
            "state": "OPEN",
            "source": {"branch": {"name": "feature/upgrade"}},
            "destination": {"branch": {"name": "main"}},
            "links": {"html": {"href": "https://bitbucket.org/ws/repo/pull-requests/12"}}
        }
    """.trimIndent()

    @Test
    fun `Reviewers given as UUIDs are kept without any call`() {
        assertEquals(
            listOf("{uuid-1}"),
            client.resolveReviewers("ws", listOf("{uuid-1}"))
        )
        server.verify()
    }

    @Test
    fun `Reviewers resolved from the workspace members`() {
        server.expect(requestTo("https://api.bitbucket.org/2.0/workspaces/ws/members?pagelen=100"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                        {
                            "values": [
                                {"user": {"uuid": "{u-1}", "account_id": "557058:1", "nickname": "alice", "display_name": "Alice A."}},
                                {"user": {"uuid": "{u-2}", "account_id": "557058:2", "nickname": "bob", "display_name": "Bob B."}}
                            ],
                            "next": "https://api.bitbucket.org/2.0/workspaces/ws/members?pagelen=100&page=2"
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("https://api.bitbucket.org/2.0/workspaces/ws/members?pagelen=100&page=2"))
            .andRespond(
                withSuccess(
                    """
                        {
                            "values": [
                                {"user": {"uuid": "{u-3}", "account_id": "557058:3", "nickname": "carol", "display_name": "Carol C."}}
                            ]
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals(
            listOf("{u-3}", "{u-1}", "{u-2}", "{uuid-4}"),
            client.resolveReviewers("ws", listOf("carol", "557058:1", "Bob B.", "{uuid-4}"))
        )
        server.verify()
    }

    @Test
    fun `Unknown reviewer`() {
        server.expect(requestTo("https://api.bitbucket.org/2.0/workspaces/ws/members?pagelen=100"))
            .andRespond(withSuccess("""{"values": []}""", MediaType.APPLICATION_JSON))
        assertThrows<BitbucketCloudReviewerNotFoundException> {
            client.resolveReviewers("ws", listOf("nobody"))
        }
    }

    @Test
    fun `Creating a pull request with reviewers`() {
        server.expect(requestTo("$repo/pullrequests"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.title").value("Upgrade"))
            .andExpect(jsonPath("$.description").value("Upgrading the version"))
            .andExpect(jsonPath("$.source.branch.name").value("feature/upgrade"))
            .andExpect(jsonPath("$.destination.branch.name").value("main"))
            .andExpect(jsonPath("$.reviewers[0].uuid").value("{u-1}"))
            .andExpect(jsonPath("$.reviewers[1].uuid").value("{u-2}"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(pr12))
        val pr = client.createPullRequest(
            workspace = "ws",
            repository = "repo",
            from = "feature/upgrade",
            to = "main",
            title = "Upgrade",
            description = "Upgrading the version",
            reviewers = listOf("{u-1}", "{u-2}"),
        )
        assertEquals(12, pr.id)
        assertEquals("https://bitbucket.org/ws/repo/pull-requests/12", pr.links?.html?.href)
        server.verify()
    }

    @Test
    fun `Approving a pull request`() {
        server.expect(requestTo("$repo/pullrequests/12/approve"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                header(
                    HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString("bot@example.com:secret".toByteArray())
                )
            )
            .andRespond(withSuccess("""{"approved": true}""", MediaType.APPLICATION_JSON))
        client.approvePullRequest("ws", "repo", 12)
        server.verify()
    }

    @Test
    fun `Statuses of a pull request`() {
        server.expect(requestTo("$repo/pullrequests/12/statuses?pagelen=100"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"values": [{"state": "SUCCESSFUL"}, {"state": "INPROGRESS"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals(listOf("SUCCESSFUL", "INPROGRESS"), client.getPullRequestStatuses("ws", "repo", 12))
        server.verify()
    }

    private fun expectMerge(strategy: String) =
        server.expect(requestTo("$repo/pullrequests/12/merge"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.type").value("pullrequest_merge_parameters"))
            .andExpect(jsonPath("$.merge_strategy").value(strategy))
            .andExpect(jsonPath("$.message").value("Merge message"))
            .andExpect(jsonPath("$.close_source_branch").value(true))

    private fun merge(strategy: String) = client.mergePullRequest(
        workspace = "ws",
        repository = "repo",
        id = 12,
        strategy = strategy,
        message = "Merge message",
        closeSourceBranch = true,
    )

    @Test
    fun `Merging a pull request with each strategy`() {
        listOf("merge_commit", "squash", "fast_forward").forEach { strategy ->
            server.reset()
            expectMerge(strategy)
                .andRespond(withSuccess(pr12.replace("OPEN", "MERGED"), MediaType.APPLICATION_JSON))
            assertEquals(BitbucketCloudMergeOutcome.MERGED, merge(strategy))
            server.verify()
        }
    }

    @Test
    fun `Merge still running`() {
        expectMerge("squash").andRespond(withStatus(HttpStatus.ACCEPTED))
        assertEquals(BitbucketCloudMergeOutcome.PENDING, merge("squash"))
        server.verify()
    }

    @Test
    fun `Pull request not mergeable yet`() {
        listOf(400, 409, 555).forEach { status ->
            server.reset()
            expectMerge("squash").andRespond(
                withRawStatus(status).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"type": "error", "error": {"message": "Merge checks failed"}}""")
            )
            assertEquals(BitbucketCloudMergeOutcome.NOT_MERGEABLE, merge("squash"), "Status $status")
            server.verify()
        }
    }

}
