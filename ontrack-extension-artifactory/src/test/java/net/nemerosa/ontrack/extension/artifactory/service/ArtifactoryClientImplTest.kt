package net.nemerosa.ontrack.extension.artifactory.service

import net.nemerosa.ontrack.extension.artifactory.client.ArtifactoryClientImpl
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import net.nemerosa.ontrack.extension.support.client.jackson2RestTemplateBuilder
import kotlin.test.assertEquals

class ArtifactoryClientImplTest {

    @Test
    fun buildNumbers() {
        val restTemplate = jackson2RestTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        val client = ArtifactoryClientImpl(restTemplate)

        server.expect(
            once(),
            requestTo("/api/build/PROJECT")
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    mapOf(
                        "buildsNumbers" to listOf(
                            mapOf(
                                "uri" to "/1"
                            ),
                            mapOf(
                                "uri" to "/2"
                            )
                        )
                    ).asJson().asJsonString(),
                    MediaType.APPLICATION_JSON
                )
            )

        assertEquals(
            listOf("1", "2"),
            client.getBuildNumbers("PROJECT")
        )

        server.verify()
    }

    @Test
    fun buildNumbersEmptyForBuildNotFound() {
        val restTemplate = jackson2RestTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(
            once(),
            requestTo("/api/build/PROJECT")
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val client = ArtifactoryClientImpl(restTemplate)

        assertEquals(
            emptyList(),
            client.getBuildNumbers("PROJECT")
        )
    }
}
