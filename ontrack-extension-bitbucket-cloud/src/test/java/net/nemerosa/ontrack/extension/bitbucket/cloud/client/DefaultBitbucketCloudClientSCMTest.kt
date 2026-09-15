package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.never
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import java.time.LocalDateTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultBitbucketCloudClientSCMTest {

    private val client = DefaultBitbucketCloudClient(
        BitbucketCloudConfiguration(
            name = "bbc",
            authType = BitbucketCloudAuthType.ACCESS_TOKEN,
            token = "secret",
        )
    )

    private val server = MockRestServiceServer.bindTo(client.template).build()

    private val repo = "https://api.bitbucket.org/2.0/repositories/ws/repo"

    private fun commit(hash: String, message: String = "Message $hash") = """
        {
            "hash": "$hash",
            "date": "2026-09-01T10:20:30+00:00",
            "message": "$message",
            "author": {"raw": "Some Author <author@example.com>", "user": {"display_name": "Some Author"}},
            "links": {"html": {"href": "https://bitbucket.org/ws/repo/commits/$hash"}}
        }
    """.trimIndent()

    @Test
    fun `Last commit of a branch`() {
        server.expect(requestTo("$repo/refs/branches/feature/one"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"name":"feature/one","target":${commit("abc")}}""", MediaType.APPLICATION_JSON))
        assertEquals("abc", client.getBranchLastCommit("ws", "repo", "feature/one"))
        server.verify()
    }

    @Test
    fun `Last commit of a missing branch`() {
        server.expect(requestTo("$repo/refs/branches/missing"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getBranchLastCommit("ws", "repo", "missing"))
        server.verify()
    }

    @Test
    fun `Creating a branch from the last commit of the source branch`() {
        server.expect(requestTo("$repo/refs/branches/main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"name":"main","target":${commit("abc")}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$repo/refs/branches"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.name").value("feature/new"))
            .andExpect(jsonPath("$.target.hash").value("abc"))
            .andRespond(
                withStatus(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"name":"feature/new","target":${commit("abc")}}""")
            )
        assertEquals("abc", client.createBranch("ws", "repo", "main", "feature/new"))
        server.verify()
    }

    @Test
    fun `Deleting a branch`() {
        server.expect(requestTo("$repo/refs/branches/feature/old"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent())
        client.deleteBranch("ws", "repo", "feature/old")
        server.verify()
    }

    @Test
    fun `Deleting a missing branch is ignored`() {
        server.expect(requestTo("$repo/refs/branches/missing"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        client.deleteBranch("ws", "repo", "missing")
        server.verify()
    }

    @Test
    fun `Downloading a file on a branch`() {
        server.expect(requestTo("$repo/src/feature/one/some/path/gradle.properties"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("version=1.0.0", MediaType.TEXT_PLAIN))
        assertContentEquals(
            "version=1.0.0".toByteArray(),
            client.download("ws", "repo", "feature/one", "some/path/gradle.properties")
        )
        server.verify()
    }

    @Test
    fun `Downloading a missing file`() {
        server.expect(requestTo("$repo/src/main/missing.txt"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.download("ws", "repo", "main", "missing.txt"))
        server.verify()
    }

    @Test
    fun `Uploading a file as a form`() {
        server.expect(requestTo("$repo/src"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"message\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Upgrade")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"branch\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("feature/one")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"some/gradle.properties\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("version=2.0.0")))
            .andRespond(withStatus(HttpStatus.CREATED))
        client.upload(
            workspace = "ws",
            repository = "repo",
            branch = "feature/one",
            path = "some/gradle.properties",
            content = "version=2.0.0".toByteArray(),
            message = "Upgrade",
        )
        server.verify()
    }

    @Test
    fun `Commits between two commits are paginated`() {
        server.expect(requestTo("$repo/commits?include=to&exclude=from&pagelen=100"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"values":[${commit("c3")},${commit("c2")}],"next":"$repo/commits?include=to&exclude=from&pagelen=100&page=xyz"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("$repo/commits?include=to&exclude=from&pagelen=100&page=xyz"))
            .andRespond(withSuccess("""{"values":[${commit("c1")}]}""", MediaType.APPLICATION_JSON))
        val commits = client.getCommits("ws", "repo", "from", "to", maxCommits = 1000)
        assertEquals(listOf("c3", "c2", "c1"), commits.map { it.hash })
        server.verify()
    }

    @Test
    fun `Commits between two commits are capped`() {
        server.expect(requestTo("$repo/commits?include=to&exclude=from&pagelen=2"))
            .andRespond(
                withSuccess(
                    """{"values":[${commit("c3")},${commit("c2")}],"next":"$repo/commits?page=xyz"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(never(), requestTo("$repo/commits?page=xyz"))
        val commits = client.getCommits("ws", "repo", "from", "to", maxCommits = 2)
        assertEquals(listOf("c3", "c2"), commits.map { it.hash })
        server.verify()
    }

    @Test
    fun `Getting a commit`() {
        server.expect(requestTo("$repo/commit/abc"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(commit("abc", "Some message"), MediaType.APPLICATION_JSON))
        val commit = client.getCommit("ws", "repo", "abc")
        assertEquals("abc", commit?.hash)
        assertEquals("Some message", commit?.message)
        assertEquals("Some Author", commit?.authorName)
        assertEquals("author@example.com", commit?.authorEmail)
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 20, 30), commit?.timestamp)
        assertEquals("https://bitbucket.org/ws/repo/commits/abc", commit?.links?.html?.href)
        server.verify()
    }

    @Test
    fun `Getting a missing commit`() {
        server.expect(requestTo("$repo/commit/missing"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getCommit("ws", "repo", "missing"))
        server.verify()
    }

    @Test
    fun `Getting a pull request`() {
        server.expect(requestTo("$repo/pullrequests/12"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                        {
                            "id": 12,
                            "title": "Some PR",
                            "state": "OPEN",
                            "source": {"branch": {"name": "feature/one"}},
                            "destination": {"branch": {"name": "main"}},
                            "links": {"html": {"href": "https://bitbucket.org/ws/repo/pull-requests/12"}}
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )
        val pr = client.getPullRequest("ws", "repo", 12)
        assertEquals(12, pr?.id)
        assertEquals("Some PR", pr?.title)
        assertEquals("OPEN", pr?.state)
        assertEquals("feature/one", pr?.source?.branch?.name)
        assertEquals("main", pr?.destination?.branch?.name)
        assertEquals("https://bitbucket.org/ws/repo/pull-requests/12", pr?.links?.html?.href)
        server.verify()
    }

    @Test
    fun `Getting a missing pull request`() {
        server.expect(requestTo("$repo/pullrequests/404"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertNull(client.getPullRequest("ws", "repo", 404))
        server.verify()
    }

    @Test
    fun `Main branch of a repository`() {
        server.expect(requestTo(repo))
            .andRespond(
                withSuccess(
                    """
                        {
                            "uuid": "{repo}", "slug": "repo", "name": "repo",
                            "project": {"uuid": "{prj}", "key": "PRJ", "name": "Project"},
                            "created_on": "2021-06-10T13:55:21.161272+00:00",
                            "updated_on": "2021-06-10T13:55:21.161272+00:00",
                            "mainbranch": {"name": "develop"}
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )
        assertEquals("develop", client.getRepository("ws", "repo").mainbranch?.name)
        server.verify()
    }
}
