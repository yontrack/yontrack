package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultGitLabClientTest {

    private val config = GitLabConfiguration(
        name = "gl",
        url = "https://gitlab.com",
        token = "secret",
    )

    /**
     * Records what the client would sleep on, so that the rate limit handling is testable.
     */
    private val slept = mutableListOf<Long>()

    private fun client(configuration: GitLabConfiguration = config) =
        DefaultGitLabClient(configuration, sleeper = { slept += it })

    @Test
    fun `The personal access token is sent on every call`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andRespond(withSuccess("""{"id":1,"username":"bot"}""", MediaType.APPLICATION_JSON))
        client.validate()
        server.verify()
    }

    @Test
    fun `A configuration URL with a trailing slash does not double the separator`() {
        val client = client(config.copy(url = "https://gitlab.example.com/"))
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.example.com/api/v4/user"))
            .andRespond(withSuccess("""{"id":1,"username":"bot"}""", MediaType.APPLICATION_JSON))
        client.validate()
        server.verify()
    }

    @Test
    fun `Validation fails when the token is refused`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        assertThrows<Exception> {
            client.validate()
        }
        server.verify()
    }

    @Test
    fun `A configuration without a token is rejected`() {
        assertThrows<IllegalStateException> {
            DefaultGitLabClient(config.copy(token = "")).template
        }
    }

    @Test
    fun `Projects are paginated by following the Link header`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        val first = "https://gitlab.com/api/v4/projects?membership=true&simple=true&order_by=path&sort=asc&per_page=100&page=1"
        val second = "https://gitlab.com/api/v4/projects?membership=true&simple=true&order_by=path&sort=asc&per_page=100&page=2"
        server.expect(requestTo(first))
            .andRespond(
                withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                    .headers(HttpHeaders().apply { set(HttpHeaders.LINK, """<$second>; rel="next"""") })
            )
        server.expect(requestTo(second))
            .andRespond(withSuccess(projectPage("group/sub/two"), MediaType.APPLICATION_JSON))
        assertEquals(
            listOf("group/one", "group/sub/two"),
            client.getProjects().map { it.path_with_namespace },
        )
        server.verify()
    }

    @Test
    fun `Pagination stops when there is no next link`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToProjects()))
            .andRespond(
                withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                    .headers(HttpHeaders().apply { set(HttpHeaders.LINK, """<https://gitlab.com/api/v4/projects?page=1>; rel="first"""") })
            )
        assertEquals(listOf("group/one"), client.getProjects().map { it.path_with_namespace })
        server.verify()
    }

    @Test
    fun `A project path is URL encoded, subgroups included`() {
        assertEquals("group%2Fsub%2Fproject", DefaultGitLabClient.encodeProjectPath("group/sub/project"))
        assertEquals("group%2Fproject", DefaultGitLabClient.encodeProjectPath("/group/project/"))
        assertEquals("group%2Fmy.project", DefaultGitLabClient.encodeProjectPath("group/my.project"))
    }

    @Test
    fun `Getting an issue of a project in a subgroup`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fsub%2Fproject/issues/12"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(issueJson(), MediaType.APPLICATION_JSON))
        val issue = client.getIssue("group/sub/project", 12)
        assertEquals(12, issue?.iid)
        assertEquals("Some issue", issue?.title)
        assertEquals("opened", issue?.state)
        assertEquals(listOf("bug", "urgent"), issue?.labels)
        assertEquals(7, issue?.milestone?.iid)
        assertEquals(
            LocalDateTime.of(2026, Month.SEPTEMBER, 19, 10, 11, 12),
            issue?.updateTime,
        )
        server.verify()
    }

    @Test
    fun `An unknown issue is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/issues/404"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getIssue("group/project", 404))
        server.verify()
    }

    @Test
    fun `Getting a merge request`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests/3"))
            .andRespond(withSuccess(mergeRequestJson(), MediaType.APPLICATION_JSON))
        val mr = client.getMergeRequest("group/project", 3)
        assertEquals(3, mr?.iid)
        assertEquals("feature/one", mr?.source_branch)
        assertEquals("main", mr?.target_branch)
        assertEquals("opened", mr?.state)
        assertEquals("https://gitlab.com/group/project/-/merge_requests/3", mr?.web_url)
        server.verify()
    }

    @Test
    fun `An unknown merge request is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests/404"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getMergeRequest("group/project", 404))
        server.verify()
    }

    @Test
    fun `A 429 is retried after the delay GitLab asks for`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, "3") })
            )
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(withSuccess("""{"id":1,"username":"bot"}""", MediaType.APPLICATION_JSON))
        client.validate()
        assertEquals(listOf(3L), slept)
        server.verify()
    }

    @Test
    fun `A 429 without Retry-After falls back on RateLimit-Reset`() {
        val now = 1_000_000L
        val client = DefaultGitLabClient(config, sleeper = { slept += it }, epochSeconds = { now })
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(HttpHeaders().apply { set("RateLimit-Reset", (now + 12).toString()) })
            )
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(withSuccess("""{"id":1,"username":"bot"}""", MediaType.APPLICATION_JSON))
        client.validate()
        assertEquals(listOf(12L), slept)
        server.verify()
    }

    @Test
    fun `A 429 with no rate limit header at all still waits`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(withSuccess("""{"id":1,"username":"bot"}""", MediaType.APPLICATION_JSON))
        client.validate()
        assertEquals(listOf(DefaultGitLabClient.DEFAULT_RETRY_SECONDS), slept)
        server.verify()
    }

    @Test
    fun `A rate limit which never lets up gives up`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        repeat(DefaultGitLabClient.MAX_RETRIES + 1) {
            server.expect(requestTo("https://gitlab.com/api/v4/user"))
                .andRespond(
                    withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, "1") })
                )
        }
        val ex = assertThrows<GitLabRateLimitException> {
            client.validate()
        }
        assertTrue(ex.message!!.contains("rate limit"), "Message names the rate limit: ${ex.message}")
        assertEquals(DefaultGitLabClient.MAX_RETRIES, slept.size)
        server.verify()
    }

    private fun requestToProjects() =
        "https://gitlab.com/api/v4/projects?membership=true&simple=true&order_by=path&sort=asc&per_page=100&page=1"

    private fun projectPage(path: String) = """
        [
            {
                "id": 1,
                "name": "${path.substringAfterLast('/')}",
                "path_with_namespace": "$path",
                "web_url": "https://gitlab.com/$path"
            }
        ]
    """.trimIndent()

    private fun issueJson() = """
        {
            "id": 1234,
            "iid": 12,
            "project_id": 7,
            "title": "Some issue",
            "state": "opened",
            "web_url": "https://gitlab.com/group/sub/project/-/issues/12",
            "labels": ["bug", "urgent"],
            "updated_at": "2026-09-19T10:11:12.000Z",
            "milestone": {
                "id": 700,
                "iid": 7,
                "title": "v1"
            }
        }
    """.trimIndent()

    private fun mergeRequestJson() = """
        {
            "id": 300,
            "iid": 3,
            "title": "Some merge request",
            "state": "opened",
            "source_branch": "feature/one",
            "target_branch": "main",
            "web_url": "https://gitlab.com/group/project/-/merge_requests/3"
        }
    """.trimIndent()

}
