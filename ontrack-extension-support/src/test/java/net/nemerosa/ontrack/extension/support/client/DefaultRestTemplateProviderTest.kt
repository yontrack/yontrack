package net.nemerosa.ontrack.extension.support.client

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject

/**
 * How a relative path is resolved against the root URI given to the provider,
 * whatever the method used by the builder to set it.
 */
class DefaultRestTemplateProviderTest {

    private fun expect(root: String, expected: String, call: RestTemplate.() -> Unit) {
        val template = DefaultRestTemplateProvider().createRestTemplate(root) { this }
        val server = MockRestServiceServer.bindTo(template).build()
        server.expect(requestTo(expected))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        template.call()
        server.verify()
    }

    @Test
    fun `path appended to a root without path`() {
        expect("https://api.example.com", "https://api.example.com/repos/x") {
            getForObject<String>("/repos/x")
        }
    }

    @Test
    fun `path appended to a root with a path`() {
        expect("https://example.com/api/v2", "https://example.com/api/v2/workspaces/x") {
            getForObject<String>("/workspaces/x")
        }
    }

    @Test
    fun `query string kept`() {
        expect("https://example.com/api", "https://example.com/api/search?q=abc&page=2") {
            getForObject<String>("/search?q=abc&page=2")
        }
    }

    @Test
    fun `path variables expanded`() {
        expect("https://example.com/api", "https://example.com/api/repos/owner/repo") {
            getForObject<String>("/repos/{owner}/{repo}", "owner", "repo")
        }
    }

    @Test
    fun `path variable with a slash encoded`() {
        expect("https://example.com/api", "https://example.com/api/refs/heads%2Ffeature%2Fx") {
            getForObject<String>("/refs/{ref}", "heads/feature/x")
        }
    }

    /**
     * With `baseUri`, variable values are encoded strictly: a `+` is sent as `%2B`
     * and no longer read as a space by the server, as it was with `rootUri`.
     */
    @Test
    fun `query variable with a space and reserved characters encoded`() {
        expect("https://example.com/api", "https://example.com/api/search?q=a%20b%3Ac%2Bd") {
            getForObject<String>("/search?q={q}", "a b:c+d")
        }
    }

    @Test
    fun `absolute URL not resolved against the root`() {
        expect("https://example.com/api", "https://other.example.com/x") {
            getForObject<String>("https://other.example.com/x")
        }
    }

}
