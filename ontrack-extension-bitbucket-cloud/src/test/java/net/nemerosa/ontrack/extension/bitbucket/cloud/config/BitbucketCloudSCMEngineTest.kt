package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitbucketCloudSCMEngineTest {

    private lateinit var bitbucketCloudConfigurationService: BitbucketCloudConfigurationService
    private lateinit var engine: BitbucketCloudSCMEngine

    @BeforeEach
    fun before() {
        bitbucketCloudConfigurationService = mockk()
        engine = BitbucketCloudSCMEngine(
            propertyService = mockk(),
            bitbucketCloudConfigurationService = bitbucketCloudConfigurationService,
            gitSCMEngineHelper = mockk(),
        )
    }

    @Test
    fun `Name of the engine`() {
        assertEquals("bitbucket-cloud", engine.name)
    }

    @Test
    fun `Matching HTTPS URL`() {
        assertTrue(engine.matchesUrl("https://bitbucket.org/my-workspace/my-repo.git"))
    }

    @Test
    fun `Matching HTTPS URL with user`() {
        assertTrue(engine.matchesUrl("https://user@bitbucket.org/my-workspace/my-repo.git"))
    }

    @Test
    fun `Matching SSH URL`() {
        assertTrue(engine.matchesUrl("git@bitbucket.org:my-workspace/my-repo.git"))
    }

    @Test
    fun `Not matching a GitHub URL`() {
        assertFalse(engine.matchesUrl("https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `Not matching a Bitbucket Server URL`() {
        assertFalse(engine.matchesUrl("https://bitbucket.dev.yontrack.com/scm/nemerosa/yontrack.git"))
    }

    @Test
    fun `Not matching a host only ending with bitbucket org`() {
        assertFalse(engine.matchesUrl("https://notbitbucket.org/my-workspace/my-repo.git"))
    }

    @Test
    fun `Parsing the workspace and repository from an HTTPS URL`() {
        assertEquals(
            "my-workspace" to "my-repo",
            engine.getWorkspaceAndRepository("https://bitbucket.org/my-workspace/my-repo.git")
        )
    }

    @Test
    fun `Parsing the workspace and repository from an HTTPS URL with user`() {
        assertEquals(
            "my-workspace" to "my-repo",
            engine.getWorkspaceAndRepository("https://user@bitbucket.org/my-workspace/my-repo.git")
        )
    }

    @Test
    fun `Parsing the workspace and repository from an SSH URL`() {
        assertEquals(
            "my-workspace" to "my-repo",
            engine.getWorkspaceAndRepository("git@bitbucket.org:my-workspace/my-repo.git")
        )
    }

    @Test
    fun `Parsing the workspace and repository from a URL without the git suffix`() {
        assertEquals(
            "my-workspace" to "my-repo",
            engine.getWorkspaceAndRepository("https://bitbucket.org/my-workspace/my-repo")
        )
    }

    @Test
    fun `Parsing a non Bitbucket Cloud URL`() {
        assertFailsWith<BitbucketCloudSCMRepositoryNotDetectedException> {
            engine.getWorkspaceAndRepository("https://github.com/nemerosa/yontrack.git")
        }
    }

    @Test
    fun `Explicit configuration`() {
        val config = bitbucketCloudTestConfigMock(name = "explicit")
        every { bitbucketCloudConfigurationService.getConfiguration("explicit") } returns config
        assertEquals(config, engine.getBitbucketCloudConfiguration("explicit"))
    }

    @Test
    fun `Single configuration`() {
        val config = bitbucketCloudTestConfigMock()
        every { bitbucketCloudConfigurationService.configurations } returns listOf(config)
        assertEquals(config, engine.getBitbucketCloudConfiguration(null))
    }

    @Test
    fun `Single configuration with a blank scmConfig`() {
        val config = bitbucketCloudTestConfigMock()
        every { bitbucketCloudConfigurationService.configurations } returns listOf(config)
        assertEquals(config, engine.getBitbucketCloudConfiguration(""))
    }

    @Test
    fun `No configuration`() {
        every { bitbucketCloudConfigurationService.configurations } returns emptyList()
        assertFailsWith<BitbucketCloudSCMNoConfigException> {
            engine.getBitbucketCloudConfiguration(null)
        }
    }

    @Test
    fun `Ambiguous configurations`() {
        every { bitbucketCloudConfigurationService.configurations } returns listOf(
            bitbucketCloudTestConfigMock(),
            bitbucketCloudTestConfigMock(),
        )
        assertFailsWith<BitbucketCloudSCMAmbiguousConfigException> {
            engine.getBitbucketCloudConfiguration(null)
        }
    }

}
