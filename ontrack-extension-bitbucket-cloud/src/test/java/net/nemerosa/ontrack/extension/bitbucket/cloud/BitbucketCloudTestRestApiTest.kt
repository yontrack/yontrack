package net.nemerosa.ontrack.extension.bitbucket.cloud

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import kotlin.test.assertEquals

class BitbucketCloudTestRestApiTest {

    private val template = restTemplateBuilder().build()
    private val server = MockRestServiceServer.bindTo(template).build()
    private val api = BitbucketCloudTestRestApi(
        template = template,
        root = "https://api.bitbucket.org",
        workspace = "ws",
        repository = "repo",
    )

    private fun path(expected: String, query: Map<String, String> = emptyMap()) = RequestMatcher { request ->
        assertEquals(expected, request.uri.path)
        val actualQuery = request.uri.query
            ?.split("&")
            ?.associate { it.substringBefore("=") to it.substringAfter("=") }
            ?: emptyMap()
        query.forEach { (name, value) -> assertEquals(value, actualQuery[name], "Query parameter $name") }
    }

    @Test
    fun `Branches are read across pages and filtered on the prefix`() {
        server.expect(once(), path("/2.0/repositories/ws/repo/refs/branches", mapOf("q" to "name ~ \"yontrack-test-\"")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"values":[{"name":"yontrack-test-a"}],"next":"https://api.bitbucket.org/2.0/repositories/ws/repo/refs/branches?page=2"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(once(), path("/2.0/repositories/ws/repo/refs/branches", mapOf("page" to "2")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"values":[{"name":"yontrack-test-b"}]}""", MediaType.APPLICATION_JSON))

        assertEquals(listOf("yontrack-test-a", "yontrack-test-b"), api.branches("yontrack-test-"))
        server.verify()
    }

    @Test
    fun `Open pull requests with their source branch`() {
        server.expect(once(), path("/2.0/repositories/ws/repo/pullrequests", mapOf("state" to "OPEN")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"values":[{"id":7,"source":{"branch":{"name":"yontrack-test-a"}}}]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertEquals(
            listOf(BitbucketCloudTestPullRequest(id = 7, sourceBranch = "yontrack-test-a")),
            api.openPullRequests()
        )
        server.verify()
    }

    @Test
    fun `Declining a pull request`() {
        server.expect(once(), path("/2.0/repositories/ws/repo/pullrequests/7/decline"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        api.declinePullRequest(7)
        server.verify()
    }

    @Test
    fun `Deleting a branch`() {
        server.expect(once(), path("/2.0/repositories/ws/repo/refs/branches/yontrack-test-a"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent())

        api.deleteBranch("yontrack-test-a")
        server.verify()
    }

}
