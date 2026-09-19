package net.nemerosa.ontrack.extension.gitlab.config

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitLabSCMEngineTest {

    private lateinit var gitLabConfigurationService: GitLabConfigurationService
    private lateinit var engine: GitLabSCMEngine

    @BeforeEach
    fun before() {
        gitLabConfigurationService = mockk()
        engine = GitLabSCMEngine(
            propertyService = mockk(),
            gitLabConfigurationService = gitLabConfigurationService,
            gitSCMEngineHelper = mockk(),
        )
    }

    @Test
    fun `Engine name`() {
        assertEquals("gitlab", engine.name)
    }

    @Test
    fun `No matching configuration`() {
        configurations(config("https://gitlab.com"))
        assertFalse(engine.matchesUrl("https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `No configuration at all`() {
        configurations()
        assertFalse(engine.matchesUrl("https://gitlab.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `Matching configuration on gitlab_com`() {
        configurations(config("https://gitlab.com"))
        assertTrue(engine.matchesUrl("git@gitlab.com:nemerosa/yontrack.git"))
    }

    @Test
    fun `Matching configuration on a self-managed instance`() {
        configurations(config("https://gitlab.dev.yontrack.com"))
        assertTrue(engine.matchesUrl("https://gitlab.dev.yontrack.com/nemerosa/tools/yontrack.git"))
    }

    @Test
    fun `A host having the configured host as a suffix does not match`() {
        configurations(config("https://gitlab.com"))
        assertFalse(engine.matchesUrl("https://gitlab.com.attacker.example/nemerosa/yontrack.git"))
    }

    @Test
    fun `Configuration detected from the URL`() {
        val config = config("https://gitlab.com")
        configurations(config)
        assertEquals(
            config,
            engine.getGitLabConfiguration(scmConfig = null, scmUrl = "https://gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `No configuration matching the URL`() {
        configurations(config("https://gitlab.dev.yontrack.com"))
        assertFailsWith<GitLabSCMNoConfigException> {
            engine.getGitLabConfiguration(scmConfig = null, scmUrl = "https://gitlab.com/nemerosa/yontrack.git")
        }
    }

    @Test
    fun `Several configurations matching the URL`() {
        configurations(
            config("https://gitlab.com", name = "one"),
            config("https://gitlab.com", name = "two"),
        )
        assertFailsWith<GitLabSCMAmbiguousConfigException> {
            engine.getGitLabConfiguration(scmConfig = null, scmUrl = "https://gitlab.com/nemerosa/yontrack.git")
        }
    }

    @Test
    fun `Several configurations matching the URL but one is named`() {
        val one = config("https://gitlab.com", name = "one")
        configurations(
            one,
            config("https://gitlab.com", name = "two"),
        )
        every { gitLabConfigurationService.getConfiguration("one") } returns one
        assertEquals(
            one,
            engine.getGitLabConfiguration(scmConfig = "one", scmUrl = "https://gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Repository with subgroups`() {
        assertEquals(
            "nemerosa/tools/ci/yontrack",
            engine.getGitLabRepository(
                config("https://gitlab.com"),
                "https://gitlab.com/nemerosa/tools/ci/yontrack.git",
            )
        )
    }

    @Test
    fun `Repository of a URL not on the instance of the configuration`() {
        assertFailsWith<GitLabSCMRepositoryNotDetectedException> {
            engine.getGitLabRepository(
                config("https://gitlab.dev.yontrack.com"),
                "https://gitlab.com/nemerosa/yontrack.git",
            )
        }
    }

    private fun configurations(vararg configurations: GitLabConfiguration) {
        every { gitLabConfigurationService.configurations } returns configurations.toList()
    }

    private fun config(url: String, name: String = "test") = GitLabConfiguration(
        name = name,
        url = url,
        token = "xxx",
    )
}
