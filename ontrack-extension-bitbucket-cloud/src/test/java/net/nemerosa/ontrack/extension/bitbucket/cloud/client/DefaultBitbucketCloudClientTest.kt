package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals

class DefaultBitbucketCloudClientTest {

    private val apiTokenConfig = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = "bot@example.com",
        token = "secret",
    )

    private val accessTokenConfig = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
        email = null,
        token = "access-secret",
    )

    private fun basic(email: String, token: String) =
        "Basic " + Base64.getEncoder().encodeToString("$email:$token".toByteArray())

    @Test
    fun `API token uses basic authentication with the email`() {
        assertEquals(
            basic("bot@example.com", "secret"),
            DefaultBitbucketCloudClient.authorizationHeader(apiTokenConfig)
        )
    }

    @Test
    fun `Access token uses a Bearer token`() {
        assertEquals(
            "Bearer access-secret",
            DefaultBitbucketCloudClient.authorizationHeader(accessTokenConfig)
        )
    }

    @Test
    fun `API token without email is rejected`() {
        assertThrows<IllegalStateException> {
            DefaultBitbucketCloudClient.authorizationHeader(apiTokenConfig.copy(email = null))
        }
    }

    @Test
    fun `Missing token is rejected`() {
        assertThrows<IllegalStateException> {
            DefaultBitbucketCloudClient.authorizationHeader(accessTokenConfig.copy(token = ""))
        }
    }

    @Test
    fun `API token validation calls the current user`() {
        val client = DefaultBitbucketCloudClient(apiTokenConfig)
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://api.bitbucket.org/2.0/user"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, basic("bot@example.com", "secret")))
            .andRespond(withSuccess("""{"display_name":"Bot"}""", MediaType.APPLICATION_JSON))
        client.validate()
        server.verify()
    }

    @Test
    fun `Access token validation calls the hook events`() {
        val client = DefaultBitbucketCloudClient(accessTokenConfig)
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://api.bitbucket.org/2.0/hook_events"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-secret"))
            .andRespond(withSuccess("""{}""", MediaType.APPLICATION_JSON))
        client.validate()
        server.verify()
    }

    @Test
    fun `Validation fails on unauthorized`() {
        val client = DefaultBitbucketCloudClient(accessTokenConfig)
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://api.bitbucket.org/2.0/hook_events"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        assertThrows<Exception> {
            client.validate()
        }
        server.verify()
    }

    @Test
    fun `Repositories of a workspace are paginated`() {
        val client = DefaultBitbucketCloudClient(accessTokenConfig)
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://api.bitbucket.org/2.0/repositories/ws?page=1"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-secret"))
            .andRespond(withSuccess(repositoryPage("repo-1", next = true), MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.bitbucket.org/2.0/repositories/ws?page=2"))
            .andRespond(withSuccess(repositoryPage("repo-2", next = false), MediaType.APPLICATION_JSON))
        val repositories = client.getRepositories("ws")
        assertEquals(listOf("repo-1", "repo-2"), repositories.map { it.slug })
        server.verify()
    }

    @Test
    fun `Repository of a workspace`() {
        val client = DefaultBitbucketCloudClient(apiTokenConfig)
        val server = MockRestServiceServer.bindTo(client.template).build()
        server.expect(requestTo("https://api.bitbucket.org/2.0/repositories/ws/repo-1"))
            .andRespond(withSuccess(repository("repo-1"), MediaType.APPLICATION_JSON))
        assertEquals("PRJ", client.getRepository("ws", "repo-1").project.key)
        server.verify()
    }

    private fun repository(slug: String) = """
        {
            "uuid": "{$slug}",
            "slug": "$slug",
            "name": "$slug",
            "project": {"uuid": "{prj}", "key": "PRJ", "name": "Project"},
            "created_on": "2021-06-10T13:55:21.161272+00:00",
            "updated_on": "2021-06-10T13:55:21.161272+00:00"
        }
    """.trimIndent()

    private fun repositoryPage(slug: String, next: Boolean) = """
        {
            "page": 1,
            "values": [${repository(slug)}]
            ${if (next) ""","next": "https://api.bitbucket.org/2.0/repositories/ws?page=2"""" else ""}
        }
    """.trimIndent()

    @Test
    fun `Date time parsing`() {
        val input = "2021-06-10T13:55:21.161272+00:00"
        val ldt = LocalDateTime.parse(input, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals(2021, ldt.year)
        assertEquals(Month.JUNE, ldt.month)
        assertEquals(10, ldt.dayOfMonth)
        assertEquals(13, ldt.hour)
        assertEquals(55, ldt.minute)
        assertEquals(21, ldt.second)
    }

}
