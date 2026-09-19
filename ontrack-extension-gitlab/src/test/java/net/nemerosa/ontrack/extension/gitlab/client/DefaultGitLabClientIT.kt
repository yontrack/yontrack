package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.TestOnGitLab
import net.nemerosa.ontrack.extension.gitlab.gitLabTestConfigReal
import net.nemerosa.ontrack.extension.gitlab.gitLabTestEnv
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests against the real gitlab.com fixture, API only: nothing here starts a pipeline, so nothing here
 * consumes a compute minute.
 *
 * They are skipped unless the fixture's credentials are set - see the module's README.
 */
class DefaultGitLabClientIT {

    private val client get() = DefaultGitLabClient(gitLabTestConfigReal())

    @TestOnGitLab
    fun `Validation with the bot token`() {
        client.validate()
    }

    @TestOnGitLab
    fun `Validation with an invalid token`() {
        assertThrows<Exception> {
            DefaultGitLabClient(gitLabTestConfigReal().copy(token = "invalid")).validate()
        }
    }

    @TestOnGitLab
    fun `The fixture project is among the projects of the bot`() {
        val projects = client.getProjects()
        assertTrue(
            projects.any { it.path_with_namespace == gitLabTestEnv.projectPath },
            "Expected ${gitLabTestEnv.projectPath} among ${projects.map { it.path_with_namespace }}",
        )
    }

    @TestOnGitLab
    fun `An unknown issue of the fixture project is null`() {
        assertNull(client.getIssue(gitLabTestEnv.projectPath, UNKNOWN_IID))
    }

    @TestOnGitLab
    fun `An unknown merge request of the fixture project is null`() {
        assertNull(client.getMergeRequest(gitLabTestEnv.projectPath, UNKNOWN_IID))
    }

    @TestOnGitLab
    fun `An unknown project is not found rather than an error`() {
        assertNull(client.getIssue("${gitLabTestEnv.group}/no-such-project", 1))
    }

    @TestOnGitLab
    fun `The token is still valid for a while`() {
        val days = gitLabTestEnv.daysBeforeTokenExpires()
        assertTrue(days > 0, "The GitLab test token has expired - re-run the wizard (see the module's README).")
    }

    @TestOnGitLab
    fun `The fixture project is readable`() {
        val project = client.getProjects().find { it.path_with_namespace == gitLabTestEnv.projectPath }
        assertNotNull(project) {
            assertTrue(it.web_url?.isNotBlank() == true, "The project has a web URL")
        }
    }

    companion object {
        /**
         * No fixture project ever gets this far: the tests create nothing that numbers issues or merge
         * requests, and this issue covers reads only.
         */
        private const val UNKNOWN_IID = 999_999
    }
}
