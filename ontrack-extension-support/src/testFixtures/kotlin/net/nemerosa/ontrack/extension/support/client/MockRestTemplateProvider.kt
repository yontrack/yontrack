package net.nemerosa.ontrack.extension.support.client

import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder

@Component
@ConditionalOnNotWebApplication
class MockRestTemplateProvider : DefaultRestTemplateProvider() {

    private var context: MockRestTemplateProviderContext? = null

    override fun createRestTemplate(
        rootUri: String,
        configuration: RestTemplateBuilder.() -> RestTemplateBuilder
    ): RestTemplate {
        val template = super.createRestTemplate(rootUri, configuration)
        context?.start(template)
        return template
    }

    fun createSession(): MockRestTemplateContext {
        val context = MockRestTemplateProviderContext()
        this.context = context
        return context
    }

    private inner class MockRestTemplateProviderContext : MockRestTemplateContext {

        private val actions = mutableListOf<MockRestTemplateAction>()
        private var mockServer: MockRestServiceServer? = null

        override fun onPostJson(uri: String, body: Any, outcome: MockRestTemplateOutcome) {
            actions += MockRestTemplatePostJsonAction(
                path = uri,
                body = body,
                outcome = outcome
            )
        }

        override fun onGetJson(
            uri: String,
            parameters: Map<String, String>,
            outcome: MockRestTemplateOutcome,
            expectedHeaders: Map<String, String>,
        ) {
            actions += MockRestTemplateGetJsonAction(
                path = uri,
                parameters = parameters,
                outcome = outcome,
                expectedHeaders = expectedHeaders,
            )
        }

        fun start(template: RestTemplate) {
            mockServer = MockRestServiceServer.createServer(template)
            mockServer?.let {
                actions.forEach { action ->
                    action.register(it)
                }
            }
        }

        override fun verify() {
            mockServer?.verify()
        }

        override fun close() {
            mockServer?.reset()
        }

    }

    private interface MockRestTemplateAction {
        fun register(mockServer: MockRestServiceServer)
    }

    private class MockRestTemplatePostJsonAction(
        private val path: String,
        private val body: Any,
        private val outcome: MockRestTemplateOutcome
    ) : MockRestTemplateAction {

        override fun register(mockServer: MockRestServiceServer) {
            mockServer
                .expect(ExpectedCount.once(), requestTo(path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(JsonBodyMatcher(body)))
                .andRespond(outcome.responseCreator)
        }

    }

    private class MockRestTemplateGetJsonAction(
        private val path: String,
        private val parameters: Map<String, String>,
        private val outcome: MockRestTemplateOutcome,
        private val expectedHeaders: Map<String, String> = emptyMap(),
    ) : MockRestTemplateAction {

        override fun register(mockServer: MockRestServiceServer) {
            @Suppress("VulnerableCodeUsages")
            val completePath = UriComponentsBuilder.fromUriString(path)
                .apply {
                    parameters.forEach { (name, value) ->
                        queryParam(
                            name, URLEncoder.encode(value, Charsets.UTF_8)
                                .replace("+", "%20")
                        )
                    }
                }
                .build()
                .toUriString()
            mockServer
                .expect(ExpectedCount.once(), requestTo(completePath))
                .andExpect(method(HttpMethod.GET))
                .andExpect { request ->
                    expectedHeaders.forEach { (name, expectedValue) ->
                        val actualValue = request.headers.getFirst(name)
                        require(actualValue == expectedValue) {
                            "Expected header '$name' to be '$expectedValue', but was '$actualValue'"
                        }
                    }
                }
                .andRespond(outcome.responseCreator)
        }

    }

}