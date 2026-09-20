package net.nemerosa.ontrack.extension.github.config

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubSCMEngineTest {

    private lateinit var gitHubConfigurationService: GitHubConfigurationService
    private lateinit var engine: GitHubSCMEngine

    @BeforeEach
    fun before() {
        gitHubConfigurationService = mockk()

        engine = GitHubSCMEngine(
            propertyService = mockk(),
            gitHubConfigurationService = gitHubConfigurationService,
            gitSCMEngineHelper = mockk(),
        )
    }

    @Test
    fun `Detection of GitHub SCM engine when no matching config is found`() {
        every { gitHubConfigurationService.configurations } returns emptyList()
        assertFalse(engine.matchesUrl("https://bitbucket.dev.yontrack.com/project/NEMEROSA/repository/YONTRACK"))
    }

    @Test
    fun `Detection of GitHub SCM engine when a matching config is found`() {
        every { gitHubConfigurationService.configurations } returns listOf(
            GitHubEngineConfiguration(
                name = "Test",
                url = "https://github.com",
                oauth2Token = "xxx",
            ),
        )
        assertTrue(engine.matchesUrl("git@github.com:nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of GitHub SCM engine rejects a host merely containing the configured one`() {
        every { gitHubConfigurationService.configurations } returns listOf(
            GitHubEngineConfiguration(
                name = "Test",
                url = "https://github.com",
                oauth2Token = "xxx",
            ),
        )
        assertFalse(engine.matchesUrl("https://github.com.attacker.example/nemerosa/ontrack.git"))
        assertFalse(engine.matchesUrl("https://attacker.example/github.com/nemerosa/ontrack.git"))
        assertFalse(engine.matchesUrl("https://github.com@attacker.example/nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of the GitHub repository when no matching config`() {
        every { gitHubConfigurationService.configurations } returns emptyList()
        assertFailsWith<GitHubSCMRepositoryNotDetectedException> {
            engine.getGitHubRepository("https://bitbucket.dev.yontrack.com/project/NEMEROSA/repository/YONTRACK")
        }
    }

    @Test
    fun `Detection of the GitHub repository when matching config`() {
        every { gitHubConfigurationService.configurations } returns listOf(
            GitHubEngineConfiguration(
                name = "Test",
                url = "https://github.com",
                oauth2Token = "xxx",
            ),
        )
        assertEquals(
            "nemerosa/yontrack",
            engine.getGitHubRepository("https://github.com/nemerosa/yontrack.git")
        )
        assertEquals(
            "nemerosa/ontrack",
            engine.getGitHubRepository("git@github.com:nemerosa/ontrack.git")
        )
    }

}