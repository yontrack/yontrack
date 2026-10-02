package net.nemerosa.ontrack.extension.github

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.git.GitExtensionFeature
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.github.service.GitHubIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.stale.StaleExtensionFeature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubIssueServiceExtensionTest {

    private lateinit var extension: GitHubIssueServiceExtension
    private lateinit var configuration: IssueServiceConfiguration
    private lateinit var configurationService: GitHubConfigurationService

    @BeforeEach
    fun init() {
        configurationService = mockk<GitHubConfigurationService>()
        val gitHubClientFactory = mockk<OntrackGitHubClientFactory>()
        extension = GitHubIssueServiceExtension(
            extensionFeature = GitHubExtensionFeature(
                GitExtensionFeature(SCMExtensionFeature(), StaleExtensionFeature())
            ),
            configurationService = configurationService,
            gitHubClientFactory = gitHubClientFactory,
        )
        val engineConfiguration = GitHubEngineConfiguration(
            "test",
            "url",
            "",
            "",
            ""
        )
        configuration = GitHubIssueServiceConfiguration(
            engineConfiguration,
            "nemerosa/ontrack"
        )
    }

    @Test
    fun issueServiceIdentifierContainsBothConfigurationAndRepository() {
        every {
            configurationService.getConfiguration("Test")
        } returns GitHubEngineConfiguration(
            "Test", null, null, null, null
        )
        val configuration = extension.getConfigurationByName("Test:nemerosa/ontrack")
        assertNotNull(configuration) {
            assertEquals("github", it.serviceId)
            assertEquals("Test:nemerosa/ontrack", it.name)
            assertTrue(configuration is GitHubIssueServiceConfiguration)
            assertEquals("Test", configuration.configuration.name)
            assertEquals("https://github.com", configuration.configuration.url)
            assertEquals("nemerosa/ontrack", configuration.repository)
        }
    }

    @Test
    fun getIssueId_full() {
        val o = extension.getIssueId(configuration, "#12")
        assertEquals("12", o)
    }

    @Test
    fun getIssueId_numeric() {
        val o = extension.getIssueId(configuration, "12")
        assertEquals("12", o)
    }

    @Test
    fun getIssueId_not_valid() {
        val o = extension.getIssueId(configuration, "mm")
        assertNull(o)
    }

    @Test
    fun extractIssueKeysFromMessage_none() {
        val keys = extension.extractIssueKeysFromMessage(configuration, "TEST-1 No GitHub issue")
        assertTrue(keys.isEmpty())
    }

    @Test
    fun extractIssueKeysFromMessage_one() {
        val keys = extension.extractIssueKeysFromMessage(configuration, "#12 One GitHub issue")
        assertEquals(
            setOf("12"),
            keys
        )
    }

    @Test
    fun extractIssueKeysFromMessage_two() {
        val keys = extension.extractIssueKeysFromMessage(configuration, "#12 Two GitHub #45 issue")
        assertEquals(
            setOf("12", "45"),
            keys
        )
    }

    @Test
    fun getIssueId_no_prefix() {
        assertEquals(14, extension.getIssueId("14").toLong())
    }

    @Test
    fun getIssueId_with_prefix() {
        assertEquals(14, extension.getIssueId("#14").toLong())
    }

    @Test
    fun `No key extracted from a reference to another repository`() {
        val keys = extension.extractIssueKeysFromMessage(
            configuration,
            "Same as nemerosa/ontrack-github-integration-test#673 and group/sub.group/project#5, see #12"
        )
        assertEquals(setOf("12"), keys)
    }

    @Test
    fun `No key extracted from a URL`() {
        val keys = extension.extractIssueKeysFromMessage(
            configuration,
            "See https://example.com/docs/page#12 and http://host/#13 (#14)"
        )
        assertEquals(setOf("14"), keys)
    }

    @Test
    fun `No key extracted from an HTML entity`() {
        val keys = extension.extractIssueKeysFromMessage(configuration, "It&#39;s fixed by #12")
        assertEquals(setOf("12"), keys)
    }

    @Test
    fun `No key extracted when followed by a word character`() {
        val keys = extension.extractIssueKeysFromMessage(configuration, "Colour #12abc and #13_x, but #14.")
        assertEquals(setOf("14"), keys)
    }

    @Test
    fun `Keys extracted from a list`() {
        val keys = extension.extractIssueKeysFromMessage(configuration, "Closes #12, #13,#14 (#15)")
        assertEquals(setOf("12", "13", "14", "15"), keys)
    }

}
