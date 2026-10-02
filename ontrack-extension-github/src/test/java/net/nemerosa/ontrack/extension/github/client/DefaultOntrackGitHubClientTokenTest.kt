package net.nemerosa.ontrack.extension.github.client

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.git.support.GitConnectionConfig
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.getForObject

class DefaultOntrackGitHubClientTokenTest {

    /**
     * The client built for an auto-versioning run is used for the dispatch and for every attempt
     * of the lookup of the workflow run, for minutes. A GitHub App installation token is valid for
     * 1 hour, so a token renewed while the client is in use must be the one sent afterwards.
     */
    @Test
    fun `The installation token renewed after the client creation is sent on the next request`() {
        val tokenService = mockk<GitHubAppTokenService>()
        every {
            tokenService.getAppInstallationToken(any(), any(), any(), any())
        } returnsMany listOf("token-1", "token-2")

        val client = DefaultOntrackGitHubClient(
            configuration = GitHubEngineConfiguration(
                name = "github",
                url = "https://github.com",
                appId = "1",
                appPrivateKey = "private-key",
                appInstallationAccountName = "organization",
            ),
            gitHubAppTokenService = tokenService,
            gitConnectionConfig = GitConnectionConfig.default,
            meterRegistry = mockk(relaxed = true),
        )

        val template = client.createGitHubRestTemplate()
        val server = MockRestServiceServer.bindTo(template).build()

        server.expect(once(), requestTo("https://api.github.com/first"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        server.expect(once(), requestTo("https://api.github.com/second"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-2"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        template.getForObject<String>("/first")
        template.getForObject<String>("/second")

        server.verify()
    }
}
