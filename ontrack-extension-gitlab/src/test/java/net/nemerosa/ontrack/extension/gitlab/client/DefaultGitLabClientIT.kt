package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.TestOnGitLab
import net.nemerosa.ontrack.extension.gitlab.gitLabTestConfigReal
import net.nemerosa.ontrack.extension.gitlab.gitLabTestEnv
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
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
    fun `An issue no commit names has no last commit`() {
        assertNull(client.getIssueLastCommit(gitLabTestEnv.projectPath, UNKNOWN_IID))
    }

    @TestOnGitLab
    fun `Searching the commits of an unknown project is null rather than an error`() {
        assertNull(client.getIssueLastCommit("${gitLabTestEnv.group}/no-such-project", 1))
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

    @TestOnGitLab
    fun `The fixture project is readable by its full path`() {
        val project = client.getProject(gitLabTestEnv.projectPath)
        assertNotNull(project) {
            assertEquals(gitLabTestEnv.projectPath, it.path_with_namespace)
            assertTrue(!it.default_branch.isNullOrBlank(), "The project has a default branch")
        }
    }

    @TestOnGitLab
    fun `An unknown project read by its path is null`() {
        assertNull(client.getProject("${gitLabTestEnv.group}/no-such-project"))
    }

    @TestOnGitLab
    fun `The default branch of the fixture project has a head`() {
        val defaultBranch = assertNotNull(client.getProject(gitLabTestEnv.projectPath)?.default_branch)
        val branch = client.getBranch(gitLabTestEnv.projectPath, defaultBranch)
        assertNotNull(branch) {
            assertEquals(defaultBranch, it.name)
            assertTrue(!it.commit?.id.isNullOrBlank(), "The branch has a head commit")
        }
    }

    @TestOnGitLab
    fun `An unknown branch has no head`() {
        assertNull(client.getBranchLastCommit(gitLabTestEnv.projectPath, "no-such-branch"))
    }

    @TestOnGitLab
    fun `Deleting a branch which does not exist is not an error`() {
        client.deleteBranch(gitLabTestEnv.projectPath, "no-such-branch")
    }

    @TestOnGitLab
    fun `The CI file of the fixture project can be downloaded`() {
        val defaultBranch = assertNotNull(client.getProject(gitLabTestEnv.projectPath)?.default_branch)
        val content = client.download(gitLabTestEnv.projectPath, defaultBranch, ".gitlab-ci.yml")
        assertNotNull(content) {
            assertTrue(it.isNotEmpty(), "The CI file is not empty")
        }
    }

    @TestOnGitLab
    fun `A file which does not exist is null`() {
        val defaultBranch = assertNotNull(client.getProject(gitLabTestEnv.projectPath)?.default_branch)
        assertNull(client.download(gitLabTestEnv.projectPath, defaultBranch, "no/such/file.txt"))
    }

    @TestOnGitLab
    fun `The head of the default branch can be read as a commit`() {
        val defaultBranch = assertNotNull(client.getProject(gitLabTestEnv.projectPath)?.default_branch)
        val head = assertNotNull(client.getBranchLastCommit(gitLabTestEnv.projectPath, defaultBranch))
        val commit = client.getCommit(gitLabTestEnv.projectPath, head)
        assertNotNull(commit) {
            assertEquals(head, it.id)
            assertTrue(!it.author_name.isNullOrBlank(), "The commit has an author")
            assertNotNull(it.committedTime, "The commit has a date")
        }
    }

    @TestOnGitLab
    fun `An unknown commit is null`() {
        assertNull(client.getCommit(gitLabTestEnv.projectPath, "0".repeat(40)))
    }

    @TestOnGitLab
    fun `Comparing the default branch with itself is empty`() {
        val defaultBranch = assertNotNull(client.getProject(gitLabTestEnv.projectPath)?.default_branch)
        assertEquals(
            emptyList(),
            client.getCommits(gitLabTestEnv.projectPath, defaultBranch, defaultBranch, 100),
        )
    }

    @TestOnGitLab
    fun `Comparing against an unknown reference is empty rather than an error`() {
        val defaultBranch = assertNotNull(client.getProject(gitLabTestEnv.projectPath)?.default_branch)
        assertEquals(
            emptyList(),
            client.getCommits(gitLabTestEnv.projectPath, defaultBranch, "no-such-ref", 100),
        )
    }

    companion object {
        /**
         * No fixture project ever gets this far: the tests create nothing that numbers issues or merge
         * requests, and this issue covers reads only.
         */
        private const val UNKNOWN_IID = 999_999
    }
}
