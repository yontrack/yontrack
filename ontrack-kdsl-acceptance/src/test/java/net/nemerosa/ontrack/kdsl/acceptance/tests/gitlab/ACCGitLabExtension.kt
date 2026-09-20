package net.nemerosa.ontrack.kdsl.acceptance.tests.gitlab

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLab
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLabConfigurationProperty
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Acceptance tests for the GitLab configurations and project property.
 */
class ACCGitLabExtension : AbstractACCDSLTestSupport() {

    @Test
    fun `Creation, obfuscation and deletion of a configuration`() {
        val confName = uid("gl_")
        ontrack.configurations.gitLab.create(
            GitLabConfiguration(
                name = confName,
                url = "https://gitlab.com",
                token = "secret",
            )
        )
        val conf = ontrack.configurations.gitLab.findByName(confName)
            ?: fail("Could not find the GitLab configuration")
        assertEquals(confName, conf.name)
        assertEquals("https://gitlab.com", conf.url)
        assertTrue(conf.token.isNullOrBlank(), "Token is not exposed")

        ontrack.configurations.gitLab.delete(confName)
        assertNull(ontrack.configurations.gitLab.findByName(confName), "Configuration has been deleted")
    }

    @Test
    fun `A self-managed configuration can ignore its SSL certificate`() {
        val confName = uid("gl_")
        ontrack.configurations.gitLab.create(
            GitLabConfiguration(
                name = confName,
                url = "https://gitlab.example.com",
                token = "secret",
                ignoreSslCertificate = true,
            )
        )
        val conf = ontrack.configurations.gitLab.findByName(confName)
            ?: fail("Could not find the GitLab configuration")
        assertEquals("https://gitlab.example.com", conf.url)
        assertTrue(conf.ignoreSslCertificate, "SSL certificate is ignored")
        assertTrue(conf.token.isNullOrBlank(), "Token is not exposed")
    }

    @Test
    fun `Setting the GitLab property on a project`() {
        val confName = uid("gl_")
        ontrack.configurations.gitLab.create(
            GitLabConfiguration(
                name = confName,
                url = "https://gitlab.com",
                token = "secret",
            )
        )
        project {
            gitLabConfigurationProperty = GitLabProjectConfigurationProperty(
                configuration = confName,
                // The repository is the full path, subgroups included
                repository = "group/subgroup/project",
                indexationInterval = 30,
            )
            assertNotNull(gitLabConfigurationProperty, "Property is set") {
                assertEquals(confName, it.configuration)
                assertEquals("group/subgroup/project", it.repository)
                assertEquals(30, it.indexationInterval)
                assertNull(it.issueServiceConfigurationIdentifier)
            }

            // Deleting the configuration removes the property
            ontrack.configurations.gitLab.delete(confName)
            assertNull(gitLabConfigurationProperty, "Property is gone with its configuration")
        }
    }
}
