package net.nemerosa.ontrack.extension.github.client

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DefaultOntrackGitHubClientTest {

    @Test
    fun `API REST URL for GitHub dot com`() {
        assertEquals(
            "https://api.github.com",
            DefaultOntrackGitHubClient.getApiRoot("https://github.com", graphql = false)
        )
    }

    @Test
    fun `API REST URL for GitHub enterprise`() {
        assertEquals(
            "https://github.company.com/api/v3",
            DefaultOntrackGitHubClient.getApiRoot("https://github.company.com", graphql = false)
        )
    }

    @Test
    fun `API GraphQL URL for GitHub dot com`() {
        assertEquals(
            "https://api.github.com",
            DefaultOntrackGitHubClient.getApiRoot("https://github.com", graphql = true)
        )
    }

    @Test
    fun `API GraphQL URL for GitHub enterprise`() {
        assertEquals(
            "https://github.company.com/api",
            DefaultOntrackGitHubClient.getApiRoot("https://github.company.com", graphql = true)
        )
    }

}