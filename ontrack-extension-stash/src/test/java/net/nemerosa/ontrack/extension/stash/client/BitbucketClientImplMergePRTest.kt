package net.nemerosa.ontrack.extension.stash.client

import net.nemerosa.ontrack.extension.stash.model.BitbucketRepository
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BitbucketClientImplMergePRTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var client: BitbucketClientImpl

    private val repo = BitbucketRepository(project = "PRJ", repository = "repo")
    private val prPath = "/rest/api/latest/projects/PRJ/repos/repo/pull-requests/12"

    @BeforeEach
    fun init() {
        val template = RestTemplate()
        server = MockRestServiceServer.bindTo(template).build()
        client = BitbucketClientImpl(
            configuration = StashConfiguration(
                name = "bitbucket",
                url = "https://bitbucket.example.com",
                user = "user",
                password = "secret",
                autoMergeUser = null,
                autoMergeToken = null,
            ),
            maxCommits = 100,
            template = template,
        )
        server.expect(once(), requestTo(prPath)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":12,"version":3}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `Merging a PR`() {
        server.expect(once(), requestTo("$prPath/merge")).andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"id":12,"state":"MERGED"}""", MediaType.APPLICATION_JSON))

        client.mergePR(repo, 12, "Merging")

        server.verify()
    }

    @Test
    fun `A merge rejected with a conflict status is reported as rejected`() {
        server.expect(once(), requestTo("$prPath/merge")).andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "errors": [
                            {
                              "message": "New changes were pushed to the target branch. Please retry the merge.",
                              "exceptionName": "com.atlassian.bitbucket.pull.PullRequestOutOfDateException"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

        val ex = assertThrows<BitbucketServerPRMergeRejectedException> {
            client.mergePR(repo, 12, "Merging")
        }
        assertEquals(12, ex.prId)
        assertContains(ex.message ?: "", "Please retry the merge")

        server.verify()
    }

    @Test
    fun `Other merge errors are left as they are`() {
        server.expect(once(), requestTo("$prPath/merge")).andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThrows<HttpServerErrorException> {
            client.mergePR(repo, 12, "Merging")
        }

        server.verify()
    }
}
