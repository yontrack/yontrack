package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.hamcrest.Matchers
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.LocalDateTime
import java.time.Month
import java.util.Base64
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
    fun `The current user is the owner of the token`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/user"))
            .andRespond(withSuccess("""{"id":7,"username":"bot"}""", MediaType.APPLICATION_JSON))
        val user = client.getCurrentUser()
        assertEquals(7L, user.id)
        assertEquals("bot", user.username)
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
    fun `A next link pointing at another host is refused`() {
        // The `Link` header comes from the remote server, and the client stamps the personal access token on
        // every request: following it off the instance would hand the token to whoever sent it.
        // See https://github.com/yontrack/yontrack/security/code-scanning/355
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToProjects()))
            .andRespond(
                withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                    .headers(
                        HttpHeaders().apply {
                            set(HttpHeaders.LINK, """<https://attacker.example/api/v4/projects?page=2>; rel="next"""")
                        }
                    )
            )
        assertEquals(listOf("group/one"), client.getProjects().map { it.path_with_namespace })
        // `MockRestServiceServer` fails on any request it was not told to expect, so a call to the foreign
        // host would fail here rather than pass silently.
        server.verify()
    }

    @Test
    fun `A next link on another scheme or port is refused`() {
        listOf(
            "http://gitlab.com/api/v4/projects?page=2",
            "https://gitlab.com:8443/api/v4/projects?page=2",
            "https://gitlab.com.attacker.example/api/v4/projects?page=2",
        ).forEach { link ->
            val client = client()
            val server = MockRestServiceServer.bindTo(client.template).build()
            server.expect(requestTo(requestToProjects()))
                .andRespond(
                    withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                        .headers(HttpHeaders().apply { set(HttpHeaders.LINK, """<$link>; rel="next"""") })
                )
            assertEquals(listOf("group/one"), client.getProjects().map { it.path_with_namespace }, "Refused: $link")
            server.verify()
        }
    }

    @Test
    fun `A next link which walks out of the API path is refused`() {
        listOf(
            // Only a prefix of `/api/v4`, not a segment of it
            "https://gitlab.com/api/v4evil?page=2",
            // Normalised back out of the API path
            "https://gitlab.com/api/v4/../../evil?page=2",
            // Credentials smuggled into the authority
            "https://someone:else@gitlab.com/api/v4/projects?page=2",
        ).forEach { link ->
            val client = client()
            val server = MockRestServiceServer.bindTo(client.template).build()
            server.expect(requestTo(requestToProjects()))
                .andRespond(
                    withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                        .headers(HttpHeaders().apply { set(HttpHeaders.LINK, """<$link>; rel="next"""") })
                )
            assertEquals(listOf("group/one"), client.getProjects().map { it.path_with_namespace }, "Refused: $link")
            server.verify()
        }
    }

    @Test
    fun `A next link outside the API path is refused`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToProjects()))
            .andRespond(
                withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                    .headers(
                        HttpHeaders().apply {
                            set(HttpHeaders.LINK, """<https://gitlab.com/-/redirect?to=evil>; rel="next"""")
                        }
                    )
            )
        assertEquals(listOf("group/one"), client.getProjects().map { it.path_with_namespace })
        server.verify()
    }

    @Test
    fun `A next link is followed on the configured instance, whatever its own URL form`() {
        val client = client(config.copy(url = "https://gitlab.example.com/"))
        val server = MockRestServiceServer.bindTo(client.template).build()
        val first =
            "https://gitlab.example.com/api/v4/projects?membership=true&simple=true&order_by=path&sort=asc&per_page=100&page=1"
        val second = "https://gitlab.example.com/api/v4/projects?page=2"
        server.expect(requestTo(first))
            .andRespond(
                withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON)
                    .headers(HttpHeaders().apply { set(HttpHeaders.LINK, """<$second>; rel="next"""") })
            )
        server.expect(requestTo(second))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andRespond(withSuccess(projectPage("group/two"), MediaType.APPLICATION_JSON))
        assertEquals(listOf("group/one", "group/two"), client.getProjects().map { it.path_with_namespace })
        server.verify()
    }

    @Test
    fun `The token travels on every request, paginated ones included`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToProjects()))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andRespond(withSuccess(projectPage("group/one"), MediaType.APPLICATION_JSON))
        client.getProjects()
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
    fun `The last commit of an issue is the most recent one mentioning it`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToCommitSearch("group%2Fproject", 12)))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    commitSearchJson(
                        commitJson("aaa", "Fixes #12", "2026-09-17T10:00:00.000Z"),
                        commitJson("ccc", "Last word on #12", "2026-09-19T10:00:00.000Z"),
                        commitJson("bbb", "More on #12", "2026-09-18T10:00:00.000Z"),
                    ),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals("ccc", client.getIssueLastCommit("group/project", 12))
        server.verify()
    }

    @Test
    fun `The issue number is searched for as a reference, hash included`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        // The `#` must reach GitLab encoded, or it would be taken for the start of a fragment and the
        // search would run on an empty term.
        server.expect(requestTo(requestToCommitSearch("group%2Fproject", 12)))
            .andExpect(queryParam("scope", "commits"))
            // The matcher reads the query as it goes on the wire, and `%2312` is the point: an
            // unencoded `#` would have started a fragment and left the search term empty.
            .andExpect(queryParam("search", "%2312"))
            .andExpect { request -> assertNull(request.uri.fragment, "The issue reference is not a fragment") }
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
        assertNull(client.getIssueLastCommit("group/project", 12))
        server.verify()
    }

    @Test
    fun `A commit mentioning a longer issue number is not a match`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToCommitSearch("group%2Fproject", 12)))
            .andRespond(
                withSuccess(
                    commitSearchJson(
                        commitJson("aaa", "Fixes #123", "2026-09-19T10:00:00.000Z"),
                        commitJson("bbb", "Fixes #12", "2026-09-17T10:00:00.000Z"),
                    ),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals("bbb", client.getIssueLastCommit("group/project", 12))
        server.verify()
    }

    @Test
    fun `No commit for an issue is null rather than an error`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToCommitSearch("group%2Fproject", 99)))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
        assertNull(client.getIssueLastCommit("group/project", 99))
        server.verify()
    }

    @Test
    fun `Searching the commits of an unknown project is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToCommitSearch("group%2Fnope", 12)))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getIssueLastCommit("group/nope", 12))
        server.verify()
    }

    @Test
    fun `A commit without a date is only kept when no dated commit matches`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(requestToCommitSearch("group%2Fproject", 12)))
            .andRespond(
                withSuccess(
                    commitSearchJson(
                        """{"id":"aaa","short_id":"aaa","title":"Fixes #12","message":"Fixes #12"}""",
                        commitJson("bbb", "More on #12", "2026-09-18T10:00:00.000Z"),
                    ),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals("bbb", client.getIssueLastCommit("group/project", 12))
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

    @Test
    fun `Getting a project by its full path`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fsub%2Fproject"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":7,"name":"project","path_with_namespace":"group/sub/project","default_branch":"main"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        val project = client.getProject("group/sub/project")
        assertEquals("group/sub/project", project?.path_with_namespace)
        assertEquals("main", project?.default_branch)
        server.verify()
    }

    @Test
    fun `An unknown project is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fnope"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getProject("group/nope"))
        server.verify()
    }

    @Test
    fun `The head of a branch, whose name is a single path segment`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        // `feature/one` is one segment of the URL, so its slash is encoded
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/branches/feature%2Fone"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andRespond(withSuccess(branchJson("feature/one", "abcdef1234"), MediaType.APPLICATION_JSON))
        assertEquals("abcdef1234", client.getBranchLastCommit("group/project", "feature/one"))
        server.verify()
    }

    @Test
    fun `The head of an unknown branch is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/branches/nope"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getBranchLastCommit("group/project", "nope"))
        server.verify()
    }

    @Test
    fun `Creating a branch returns the head of the new branch`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(
            requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/branches?branch=release%2F1.0&ref=main")
        )
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(branchJson("release/1.0", "1234abcd"), MediaType.APPLICATION_JSON))
        assertEquals("1234abcd", client.createBranch("group/project", "main", "release/1.0"))
        server.verify()
    }

    @Test
    fun `Creating a branch GitLab does not describe back is an error`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(Matchers.startsWith("https://gitlab.com/api/v4/projects/group%2Fproject/repository/branches")))
            .andRespond(withSuccess("""{"name":"release/1.0"}""", MediaType.APPLICATION_JSON))
        assertThrows<GitLabCannotCreateBranchException> {
            client.createBranch("group/project", "main", "release/1.0")
        }
        server.verify()
    }

    @Test
    fun `Deleting a branch`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/branches/feature%2Fone"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))
        client.deleteBranch("group/project", "feature/one")
        server.verify()
    }

    @Test
    fun `Deleting a branch which does not exist is not an error`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/branches/nope"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        client.deleteBranch("group/project", "nope")
        server.verify()
    }

    @Test
    fun `Downloading a file at a ref`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(
            requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/src%2Fmain%2Fapp.yaml/raw?ref=main")
        )
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andRespond(withSuccess("name: app", MediaType.TEXT_PLAIN))
        assertEquals("name: app", client.download("group/project", "main", "src/main/app.yaml")?.decodeToString())
        server.verify()
    }

    @Test
    fun `Downloading a file which does not exist is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(Matchers.startsWith("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.download("group/project", "main", "nope.yaml"))
        server.verify()
    }

    @Test
    fun `Downloading a file which is not there yet is retried when the caller asks for it`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        val uri = "https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/app.yaml/raw?ref=main"
        server.expect(requestTo(uri)).andRespond(withStatus(HttpStatus.NOT_FOUND))
        server.expect(requestTo(uri)).andRespond(withSuccess("name: app", MediaType.TEXT_PLAIN))
        assertEquals(
            "name: app",
            client.download("group/project", "main", "app.yaml", retryOnNotFound = true)?.decodeToString(),
        )
        assertEquals(listOf(DefaultGitLabClient.NOT_FOUND_RETRY_SECONDS), slept)
        server.verify()
    }

    @Test
    fun `Downloading a file which stays missing gives up after a bounded number of retries`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        val uri = "https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/app.yaml/raw?ref=main"
        repeat(DefaultGitLabClient.NOT_FOUND_RETRIES) {
            server.expect(requestTo(uri)).andRespond(withStatus(HttpStatus.NOT_FOUND))
        }
        assertNull(client.download("group/project", "main", "app.yaml", retryOnNotFound = true))
        assertEquals(DefaultGitLabClient.NOT_FOUND_RETRIES - 1, slept.size)
        server.verify()
    }

    @Test
    fun `Downloading a file which is not there is not retried by default`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(Matchers.startsWith("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.download("group/project", "main", "app.yaml"))
        assertTrue(slept.isEmpty(), "Nothing was waited for")
        server.verify()
    }

    /**
     * The file is read before being written, which says which verb to use and gives the `last_commit_id`
     * that protects the write against a concurrent one.
     */
    @Test
    fun `Uploading a file replaces it and sends back its last commit id`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/app.yaml?ref=main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"file_path":"app.yaml","ref":"main","blob_id":"b1","last_commit_id":"c1"}""",
                    MediaType.APPLICATION_JSON,
                )
            )
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/app.yaml"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.branch").value("main"))
            .andExpect(jsonPath("$.encoding").value("base64"))
            .andExpect(jsonPath("$.commit_message").value("Some message"))
            .andExpect(jsonPath("$.last_commit_id").value("c1"))
            .andExpect(jsonPath("$.content").value(Base64.getEncoder().encodeToString("name: app".toByteArray())))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        client.upload("group/project", "main", "app.yaml", "name: app".toByteArray(), "Some message")
        server.verify()
    }

    @Test
    fun `Uploading a file which is not there creates it`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/app.yaml?ref=main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/files/app.yaml"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.branch").value("main"))
            .andExpect(jsonPath("$.last_commit_id").doesNotExist())
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        client.upload("group/project", "main", "app.yaml", "name: app".toByteArray(), "Some message")
        server.verify()
    }

    @Test
    fun `Creating a merge request`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andExpect(jsonPath("$.source_branch").value("feature/one"))
            .andExpect(jsonPath("$.target_branch").value("main"))
            .andExpect(jsonPath("$.title").value("Some title"))
            .andExpect(jsonPath("$.description").value("Some description"))
            .andExpect(jsonPath("$.remove_source_branch").value(true))
            .andExpect(jsonPath("$.squash").value(true))
            .andExpect(jsonPath("$.reviewer_ids[0]").value(42))
            // Deprecated since 16.0, and a Premium concept: never sent
            .andExpect(jsonPath("$.approvals_before_merge").doesNotExist())
            .andRespond(withSuccess(mergeRequestJson(iid = 12), MediaType.APPLICATION_JSON))
        val mr = client.createMergeRequest(
            project = "group/project",
            sourceBranch = "feature/one",
            targetBranch = "main",
            title = "Some title",
            description = "Some description",
            reviewerIds = listOf(42),
            removeSourceBranch = true,
            squash = true,
        )
        assertEquals(12L, mr.iid, "iid of the merge request")
        assertEquals("abcdef", mr.sha)
        assertEquals("mergeable", mr.detailed_merge_status)
        assertTrue(mr.squash_on_merge, "The squash is read back from squash_on_merge")
        server.verify()
    }

    @Test
    fun `Creating a merge request without reviewers sends none`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests"))
            .andExpect(jsonPath("$.reviewer_ids").doesNotExist())
            .andRespond(withSuccess(mergeRequestJson(iid = 12), MediaType.APPLICATION_JSON))
        client.createMergeRequest(
            project = "group/project",
            sourceBranch = "feature/one",
            targetBranch = "main",
            title = "Some title",
            description = "Some description",
            reviewerIds = emptyList(),
            removeSourceBranch = false,
            squash = false,
        )
        server.verify()
    }

    @Test
    fun `Approving a merge request`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests/12/approve"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        client.approveMergeRequest("group/project", 12)
        server.verify()
    }

    @Test
    fun `Merging a merge request always sends the sha`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests/12/merge"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.sha").value("abcdef"))
            .andExpect(jsonPath("$.merge_commit_message").value("Some message"))
            .andExpect(jsonPath("$.squash").value(true))
            .andExpect(jsonPath("$.squash_commit_message").value("Some message"))
            .andExpect(jsonPath("$.should_remove_source_branch").value(true))
            .andExpect(jsonPath("$.auto_merge").doesNotExist())
            .andRespond(withSuccess(mergeRequestJson(iid = 12, state = "merged"), MediaType.APPLICATION_JSON))
        val merged = client.mergeMergeRequest(
            project = "group/project",
            iid = 12,
            sha = "abcdef",
            message = "Some message",
            squash = true,
            removeSourceBranch = true,
            autoMerge = false,
        )
        assertEquals("merged", merged.state)
        server.verify()
    }

    /**
     * `auto_merge`, not `merge_when_pipeline_succeeds`, deprecated in 17.11.
     */
    @Test
    fun `Asking GitLab to merge on its own sends auto merge`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/merge_requests/12/merge"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.sha").value("abcdef"))
            .andExpect(jsonPath("$.auto_merge").value(true))
            .andExpect(jsonPath("$.merge_when_pipeline_succeeds").doesNotExist())
            .andRespond(withSuccess(mergeRequestJson(iid = 12), MediaType.APPLICATION_JSON))
        client.mergeMergeRequest(
            project = "group/project",
            iid = 12,
            sha = "abcdef",
            message = "Some message",
            squash = false,
            removeSourceBranch = true,
            autoMerge = true,
        )
        server.verify()
    }

    @Test
    fun `Looking a user up by its exact username`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/users?username=alice"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""[{"id":42,"username":"alice"}]""", MediaType.APPLICATION_JSON))
        assertEquals(42, client.findUserByUsername("alice")?.id)
        server.verify()
    }

    @Test
    fun `An unknown user is null rather than an error`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/users?username=ghost"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
        assertNull(client.findUserByUsername("ghost"))
        server.verify()
    }


    @Test
    fun `Comparing two references goes through the merge base and returns the commits most recent first`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(
            requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/compare?from=v1&to=v2&straight=false")
        )
            .andExpect(method(HttpMethod.GET))
            // `straight=false` is the merge-base comparison, `from...to`
            .andExpect(queryParam("straight", "false"))
            .andRespond(
                withSuccess(
                    compareJson(
                        // GitLab returns them oldest first
                        commitJson("aaa", "First", "2026-09-17T10:00:00.000Z"),
                        commitJson("bbb", "Second", "2026-09-18T10:00:00.000Z"),
                        commitJson("ccc", "Third", "2026-09-19T10:00:00.000Z"),
                    ),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals(
            listOf("ccc", "bbb", "aaa"),
            client.getCommits("group/project", "v1", "v2", 100).map { it.id },
        )
        server.verify()
    }

    @Test
    fun `A comparison is capped at the maximum number of commits`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(Matchers.startsWith("https://gitlab.com/api/v4/projects/group%2Fproject/repository/compare")))
            .andRespond(
                withSuccess(
                    compareJson(
                        commitJson("aaa", "First", "2026-09-17T10:00:00.000Z"),
                        commitJson("bbb", "Second", "2026-09-18T10:00:00.000Z"),
                        commitJson("ccc", "Third", "2026-09-19T10:00:00.000Z"),
                    ),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals(listOf("ccc", "bbb"), client.getCommits("group/project", "v1", "v2", 2).map { it.id })
        server.verify()
    }

    @Test
    fun `Comparing against an unknown reference is empty rather than an error`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo(Matchers.startsWith("https://gitlab.com/api/v4/projects/group%2Fproject/repository/compare")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertEquals(emptyList(), client.getCommits("group/project", "v1", "nope", 100))
        server.verify()
    }

    @Test
    fun `Getting a single commit`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/commits/abcdef"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                        {
                            "id": "abcdef",
                            "short_id": "abcdef",
                            "title": "Some commit",
                            "message": "Some commit\n\nWith a body",
                            "committed_date": "2026-09-19T10:00:00.000Z",
                            "author_name": "A Bot",
                            "author_email": "bot@example.com",
                            "web_url": "https://gitlab.com/group/project/-/commit/abcdef"
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )
        val commit = client.getCommit("group/project", "abcdef")
        assertEquals("abcdef", commit?.id)
        assertEquals("A Bot", commit?.author_name)
        assertEquals("bot@example.com", commit?.author_email)
        assertEquals(
            LocalDateTime.of(2026, Month.SEPTEMBER, 19, 10, 0, 0),
            commit?.committedTime,
        )
        server.verify()
    }

    @Test
    fun `An unknown commit is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/repository/commits/nope"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getCommit("group/project", "nope"))
        server.verify()
    }

    @Test
    fun `Triggering a pipeline sends the ref and the variables as an array of hashes`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fsub%2Fproject/pipeline"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andExpect(
                content().json(
                    """
                        {
                            "ref": "main",
                            "variables": [
                                {"key": "VERSION", "value": "1.0.0"},
                                {"key": "TARGET", "value": "prod"}
                            ]
                        }
                    """.trimIndent(),
                    true
                )
            )
            .andRespond(withSuccess(pipelineJson(status = "created"), MediaType.APPLICATION_JSON))
        val pipeline = client.triggerPipeline(
            "group/sub/project",
            "main",
            linkedMapOf("VERSION" to "1.0.0", "TARGET" to "prod"),
        )
        assertEquals(61L, pipeline.id)
        assertEquals(21L, pipeline.iid)
        assertEquals("created", pipeline.status)
        assertEquals(false, pipeline.completed)
        server.verify()
    }

    @Test
    fun `Triggering a pipeline without any variable sends an empty array`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/pipeline"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"ref": "release/1.0", "variables": []}""", true))
            .andRespond(withSuccess(pipelineJson(), MediaType.APPLICATION_JSON))
        client.triggerPipeline("group/project", "release/1.0", emptyMap())
        server.verify()
    }

    @Test
    fun `The token never travels in the body of a pipeline trigger`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/pipeline"))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andExpect(content().string(Matchers.not(Matchers.containsString("secret"))))
            .andRespond(withSuccess(pipelineJson(), MediaType.APPLICATION_JSON))
        client.triggerPipeline("group/project", "main", mapOf("VERSION" to "1.0.0"))
        server.verify()
    }

    @Test
    fun `A pipeline GitLab does not describe back is an error`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/pipeline"))
            .andRespond(withStatus(HttpStatus.CREATED))
        assertThrows<GitLabCannotTriggerPipelineException> {
            client.triggerPipeline("group/project", "main", emptyMap())
        }
        server.verify()
    }

    @Test
    fun `Getting a pipeline by its instance id`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/pipelines/61"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(pipelineJson(status = "success"), MediaType.APPLICATION_JSON))
        val pipeline = client.getPipeline("group/project", 61)
        assertEquals("success", pipeline?.status)
        assertEquals(true, pipeline?.completed)
        server.verify()
    }

    @Test
    fun `An unknown pipeline is null`() {
        val client = client()
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://gitlab.com/api/v4/projects/group%2Fproject/pipelines/999"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getPipeline("group/project", 999))
        server.verify()
    }

    private fun pipelineJson(status: String = "pending") = """
        {
            "id": 61,
            "iid": 21,
            "project_id": 7,
            "ref": "main",
            "sha": "abcdef",
            "status": "$status",
            "source": "api",
            "web_url": "https://gitlab.com/group/project/-/pipelines/61"
        }
    """.trimIndent()

    private fun branchJson(name: String, commitId: String) = """
        {
            "name": "$name",
            "commit": {
                "id": "$commitId",
                "short_id": "${commitId.take(8)}",
                "title": "Head of $name"
            }
        }
    """.trimIndent()

    private fun compareJson(vararg commits: String) = """
        {
            "commit": ${commits.lastOrNull() ?: "null"},
            "commits": [${commits.joinToString(",")}],
            "compare_timeout": false,
            "compare_same_ref": false
        }
    """.trimIndent()

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

    private fun requestToCommitSearch(encodedProject: String, iid: Int) =
        "https://gitlab.com/api/v4/projects/$encodedProject/search?scope=commits&search=%23$iid&per_page=100"

    private fun commitSearchJson(vararg commits: String) = commits.joinToString(",", "[", "]")

    private fun commitJson(id: String, message: String, committedDate: String) = """
        {
            "id": "$id",
            "short_id": "${id.take(8)}",
            "title": "${message.lineSequence().first()}",
            "message": "$message",
            "committed_date": "$committedDate"
        }
    """.trimIndent()

    private fun mergeRequestJson(
        iid: Int = 3,
        state: String = "opened",
        detailedMergeStatus: String = "mergeable",
    ) = """
        {
            "id": ${iid * 100},
            "iid": $iid,
            "title": "Some merge request",
            "state": "$state",
            "source_branch": "feature/one",
            "target_branch": "main",
            "web_url": "https://gitlab.com/group/project/-/merge_requests/$iid",
            "sha": "abcdef",
            "detailed_merge_status": "$detailedMergeStatus",
            "squash_on_merge": true,
            "has_conflicts": false
        }
    """.trimIndent()

}
