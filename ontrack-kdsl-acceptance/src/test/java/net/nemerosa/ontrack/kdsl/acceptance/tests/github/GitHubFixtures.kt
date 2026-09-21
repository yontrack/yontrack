package net.nemerosa.ontrack.kdsl.acceptance.tests.github

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.connector.support.restTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestTemplate

/**
 * Playground client
 */

val gitHubClient: RestTemplate by lazy {
    restTemplateBuilder()
        .rootUri("https://api.github.com")
        .defaultHeader(
            HttpHeaders.AUTHORIZATION,
            "Bearer ${ACCProperties.GitHub.token}"
        )
        .build()
}
