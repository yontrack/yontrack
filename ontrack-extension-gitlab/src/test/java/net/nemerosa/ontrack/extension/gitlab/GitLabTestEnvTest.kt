package net.nemerosa.ontrack.extension.gitlab

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitLabTestEnvTest {

    private val complete = mapOf(
        GitLabTestProperties.GROUP to "yontrack-test",
        GitLabTestProperties.PROJECT to "yontrack-fixture",
        GitLabTestProperties.TOKEN to "bot-token",
        GitLabTestProperties.TOKEN_EXPIRY to "2027-09-01",
    )

    @Test
    fun `Real tests are skipped without any credentials`() {
        assertFalse(gitLabTestEnabled { null })
    }

    @Test
    fun `Real tests are skipped when explicitly ignored, even with credentials`() {
        val values = complete + (GitLabTestProperties.IGNORE to "true")
        assertFalse(gitLabTestEnabled(values::get))
    }

    @Test
    fun `Real tests are enabled with all the credentials`() {
        assertTrue(gitLabTestEnabled(complete::get))
    }

    @Test
    fun `A partial set of credentials is a misconfiguration, not a skip`() {
        val values = complete - GitLabTestProperties.TOKEN
        val ex = assertThrows<IllegalStateException> {
            gitLabTestEnabled(values::get)
        }
        assertTrue(GitLabTestProperties.TOKEN in (ex.message ?: ""), ex.message)
    }

    @Test
    fun `Pipeline tests need their own switch on top of the credentials`() {
        assertFalse(
            gitLabPipelinesTestEnabled(complete::get),
            "Credentials alone do not start pipelines: that is what integration shard 5 runs with",
        )
        val values = complete + (GitLabTestProperties.PIPELINES to "true")
        assertTrue(gitLabPipelinesTestEnabled(values::get))
    }

    @Test
    fun `The pipeline switch is worthless without the credentials`() {
        val values = mapOf(GitLabTestProperties.PIPELINES to "true")
        assertFalse(gitLabPipelinesTestEnabled(values::get))
    }

    @Test
    fun `The pipeline switch does not override the ignore flag`() {
        val values = complete +
                (GitLabTestProperties.PIPELINES to "true") +
                (GitLabTestProperties.IGNORE to "true")
        assertFalse(gitLabPipelinesTestEnabled(values::get))
    }

    /**
     * A leftover of a killed run has to be recognisable and datable, and two runs must never collide.
     */
    @Test
    fun `A test branch name carries its prefix and is unique`() {
        val first = gitLabTestBranch()
        val second = gitLabTestBranch()
        assertTrue(first.startsWith(GITLAB_TEST_BRANCH_PREFIX), first)
        assertTrue(first != second, "Two names of the same run differ")
        assertTrue(
            gitLabTestBranch("cleanup").startsWith("${GITLAB_TEST_BRANCH_PREFIX}cleanup-"),
            "The purpose is part of the name",
        )
    }

    @Test
    fun `Reading the environment`() {
        val env = readGitLabTestEnv(complete::get)
        assertEquals("yontrack-test", env.group)
        assertEquals("yontrack-fixture", env.project)
        assertEquals("yontrack-test/yontrack-fixture", env.projectPath)
        assertEquals("bot-token", env.token)
        assertEquals(LocalDate.of(2027, 9, 1), env.tokenExpiry)
    }

    @Test
    fun `Days left before the token expires`() {
        val env = readGitLabTestEnv(complete::get)
        assertEquals(31, env.daysBeforeTokenExpires(LocalDate.of(2027, 8, 1)))
        assertEquals(-1, env.daysBeforeTokenExpires(LocalDate.of(2027, 9, 2)))
    }

    @Test
    fun `System property names map to the CI environment variable names`() {
        assertEquals(
            "ONTRACK_TEST_EXTENSION_GITLAB_TOKEN_EXPIRY",
            GitLabTestProperties.envName(GitLabTestProperties.TOKEN_EXPIRY)
        )
        assertEquals(
            "ONTRACK_TEST_EXTENSION_GITLAB_GROUP",
            GitLabTestProperties.envName(GitLabTestProperties.GROUP)
        )
        assertEquals(
            "ONTRACK_TEST_EXTENSION_GITLAB_PIPELINES",
            GitLabTestProperties.envName(GitLabTestProperties.PIPELINES)
        )
    }

}
