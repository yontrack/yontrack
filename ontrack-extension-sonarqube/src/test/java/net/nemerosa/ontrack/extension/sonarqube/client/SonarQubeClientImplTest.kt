package net.nemerosa.ontrack.extension.sonarqube.client

import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SonarQubeClientImplTest {

    @Test
    fun `Encoding of parameters`() {
        val client = SonarQubeClientImpl(SonarQubeConfiguration("SonarQube", "https://localhost:9000", "token"))
        val uriTemplateHandler = client.restTemplate.uriTemplateHandler
        val uri = uriTemplateHandler.expand("/end-point?name={value}", mapOf(
                "value" to "a+0"
        )).toString()
        assertEquals("https://localhost:9000/end-point?name=a%2B0", uri)
    }

}