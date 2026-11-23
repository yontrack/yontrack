package net.nemerosa.ontrack.extension.gitlab.scm

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClient
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * [net.nemerosa.ontrack.extension.scm.service.SCM.getCloneAccess] for GitLab: the HTTPS clone URL,
 * without credentials, and the personal access token under the user name the local clones use.
 */
class GitLabSCMExtensionCloneAccessTest {

    private val client = mockk<GitLabClient>().apply {
        every { getProject(any()) } answers {
            val path = firstArg<String>()
            if (path == "group/subgroup/project") GitLabProject(path_with_namespace = path, default_branch = "main") else null
        }
    }

    private val configurationService = mockk<GitLabConfigurationService>()

    private val extension = GitLabSCMExtension(
        extensionFeature = mockk<GitLabExtensionFeature>(relaxed = true),
        propertyService = mockk(),
        structureService = mockk(),
        clientFactory = mockk<GitLabClientFactory>().apply {
            every { create(any()) } returns client
        },
        cachedSettingsService = mockk(),
        configurationService = configurationService,
        issueServiceRegistry = mockk(),
        issueServiceExtension = mockk(),
        gitRepositoryClientFactory = mockk(),
        gitConfigService = mockk(),
    )

    private fun cloneAccess(configuration: GitLabConfiguration): SCMCloneAccess {
        every { configurationService.findConfiguration(configuration.name) } returns configuration
        val (scm, _) = extension.getSCMPath(configuration.name, "group/subgroup/project/ontrack.yaml")
            ?: fail("SCM path should have been found")
        return assertNotNull(scm.getCloneAccess(), "GitLab supports the clone access")
    }

    @Test
    fun `Token under the oauth2 user name`() {
        val access = cloneAccess(
            GitLabConfiguration(name = "gl", url = "https://gitlab.example.com", token = "secret")
        )
        assertEquals("https://gitlab.example.com/group/subgroup/project.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("oauth2", credentials.username)
        assertEquals("secret", credentials.password)
    }

    @Test
    fun `No token means an anonymous access`() {
        val access = cloneAccess(
            GitLabConfiguration(name = "gl", url = "https://gitlab.com", token = null)
        )
        assertEquals("https://gitlab.com/group/subgroup/project.git", access.url)
        assertNull(access.credentials)
    }

}
