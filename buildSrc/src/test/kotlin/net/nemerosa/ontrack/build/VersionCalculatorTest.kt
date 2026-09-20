package net.nemerosa.ontrack.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException

/**
 * The branch is what drives the whole version computation, and under GitHub Actions the
 * checkout is detached, so `git rev-parse --abbrev-ref HEAD` answers `HEAD` instead of the
 * branch name. These tests pin the three cases that matter: the two release-producing
 * branches under Actions, and the unchanged local behaviour when GITHUB_REF_NAME is absent.
 */
class VersionCalculatorTest {

    private fun calculator(
        githubRefName: String? = null,
        gitBranch: String = "HEAD",
        versionFile: String? = "5.2",
        tags: List<String> = emptyList(),
        shortCommit: String = "abc1234",
    ) = VersionCalculator(
        githubRefName = githubRefName,
        gitBranch = { gitBranch },
        versionFile = { versionFile },
        gitTags = { tags },
        gitShortCommit = { shortCommit },
    )

    // --- Branch detection ---

    @Test
    fun `branch comes from GITHUB_REF_NAME when it is set`() {
        assertEquals("main", calculator(githubRefName = "main", gitBranch = "HEAD").currentBranch())
    }

    @Test
    fun `branch falls back to git when GITHUB_REF_NAME is not set`() {
        assertEquals("my-branch", calculator(githubRefName = null, gitBranch = "my-branch").currentBranch())
    }

    @Test
    fun `branch falls back to git when GITHUB_REF_NAME is blank`() {
        assertEquals("my-branch", calculator(githubRefName = "  ", gitBranch = "my-branch").currentBranch())
    }

    // --- GITHUB_REF_NAME=main -> main versioning ---

    @Test
    fun `main under GitHub Actions produces a main version`() {
        val version = calculator(
            githubRefName = "main",
            gitBranch = "HEAD",
            versionFile = "5.2",
            tags = listOf("5.2.0", "5.2.1", "5.1.9"),
        ).computeVersion()
        assertEquals("5.2.2", version)
    }

    @Test
    fun `main under GitHub Actions with no matching tag starts at zero`() {
        val version = calculator(
            githubRefName = "main",
            gitBranch = "HEAD",
            versionFile = "5.3",
            tags = listOf("5.2.0", "5.2.1"),
        ).computeVersion()
        assertEquals("5.3.0", version)
    }

    @Test
    fun `main under GitHub Actions honours a qualifier in the VERSION file`() {
        val version = calculator(
            githubRefName = "main",
            gitBranch = "HEAD",
            versionFile = "5.3-beta",
            tags = listOf("5.3-beta.0", "5.3-beta.3", "5.3.7"),
        ).computeVersion()
        assertEquals("5.3-beta.4", version)
    }

    // --- GITHUB_REF_NAME=release/5.2 -> release versioning ---

    @Test
    fun `release branch under GitHub Actions produces a release version`() {
        val version = calculator(
            githubRefName = "release/5.2",
            gitBranch = "HEAD",
            versionFile = "5.3",
            tags = listOf("5.2.0", "5.2.1", "5.3.4"),
        ).computeVersion()
        assertEquals("5.2.2", version)
    }

    /**
     * The patch-release case as it is actually run (#1702): `main` has moved on to 5.4 and its
     * VERSION file says so, and a defect in 5.3 is fixed on `release/5.3`. The version has to come
     * from the branch and the 5.3 tags alone - the VERSION file is not consulted on a release
     * branch, and the 5.4 tags main has published since must not shift the patch number.
     */
    @Test
    fun `a patch on the previous minor ignores the VERSION file and the newer tags`() {
        val version = calculator(
            githubRefName = "release/5.3",
            gitBranch = "HEAD",
            versionFile = "5.4",
            tags = listOf("5.3.0", "5.3.1", "5.4.0", "5.4.1", "5.4.12"),
        ).computeVersion()
        assertEquals("5.3.2", version)
    }

    /**
     * `findLatestPatchVersion` anchors its pattern at both ends, which is what keeps `5.3.10` and
     * `5.3.1-rc-4` from being read as patches of 5.3. Pinned because a released `5.3.10` losing to
     * `5.3.2` would republish a version that already shipped.
     */
    @Test
    fun `a patch takes the highest patch number, not the last tag`() {
        val version = calculator(
            githubRefName = "release/5.3",
            gitBranch = "HEAD",
            versionFile = "5.4",
            tags = listOf("5.3.0", "5.3.10", "5.3.2", "5.3.1-rc-4", "5.31.0"),
        ).computeVersion()
        assertEquals("5.3.11", version)
    }

    @Test
    fun `release branch under GitHub Actions with no tag starts at zero`() {
        val version = calculator(
            githubRefName = "release/5.4",
            gitBranch = "HEAD",
            versionFile = "5.3",
            tags = listOf("5.2.0"),
        ).computeVersion()
        assertEquals("5.4.0", version)
    }

    @Test
    fun `a malformed release branch is rejected`() {
        assertFailsWith<GradleException> {
            calculator(githubRefName = "release/five-two", gitBranch = "HEAD").computeVersion()
        }
    }

    // --- GITHUB_REF_NAME unset -> existing git behaviour unchanged ---

    @Test
    fun `without GITHUB_REF_NAME main still comes from git`() {
        val version = calculator(
            githubRefName = null,
            gitBranch = "main",
            versionFile = "5.2",
            tags = listOf("5.2.0"),
        ).computeVersion()
        assertEquals("5.2.1", version)
    }

    @Test
    fun `without GITHUB_REF_NAME a release branch still comes from git`() {
        val version = calculator(
            githubRefName = null,
            gitBranch = "release/5.2",
            versionFile = "5.3",
            tags = listOf("5.2.3"),
        ).computeVersion()
        assertEquals("5.2.4", version)
    }

    @Test
    fun `without GITHUB_REF_NAME a feature branch still produces a feature version`() {
        val version = calculator(
            githubRefName = null,
            gitBranch = "claude/github-actions-pipeline",
            versionFile = "5.3",
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("5.3-claude-github-actions-pipeline-deadbee", version)
    }

    // --- GITHUB_REF_NAME=v6 -> main versioning ---

    /**
     * The next-major development branch (`v6`) is a second `main`: its own VERSION file, its own
     * promotion chain, its own environment. So it versions like `main` - from the VERSION file it
     * carries and the tags of that major - and not like a feature branch.
     */
    @Test
    fun `a major branch under GitHub Actions versions like main`() {
        val version = calculator(
            githubRefName = "v6",
            gitBranch = "HEAD",
            versionFile = "6.0",
            tags = listOf("5.5.0", "5.5.1"),
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("6.0.0", version)
    }

    @Test
    fun `a major branch takes the next patch of its own major`() {
        val version = calculator(
            githubRefName = "v6",
            gitBranch = "HEAD",
            versionFile = "6.0",
            tags = listOf("6.0.0", "6.0.3", "5.5.9"),
        ).computeVersion()
        assertEquals("6.0.4", version)
    }

    /**
     * Digits only, and a full match: a branch working *towards* the next major is still a feature
     * branch, and must keep the feature version shape.
     */
    @Test
    fun `a branch named after a major but with a suffix is a feature branch`() {
        val version = calculator(
            githubRefName = "v6-spring-boot-4",
            gitBranch = "HEAD",
            versionFile = "6.0",
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("6.0-v6-spring-boot-4-deadbee", version)
    }

    // --- Feature branches ---

    @Test
    fun `a feature branch under GitHub Actions produces a feature version`() {
        val version = calculator(
            githubRefName = "claude/github-actions-pipeline",
            gitBranch = "HEAD",
            versionFile = "5.3",
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("5.3-claude-github-actions-pipeline-deadbee", version)
    }

    @Test
    fun `a pull request ref produces a feature version`() {
        val version = calculator(
            githubRefName = "42/merge",
            gitBranch = "HEAD",
            versionFile = "5.3",
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("5.3-42-merge-deadbee", version)
    }

    @Test
    fun `a missing VERSION file is rejected`() {
        assertFailsWith<GradleException> {
            calculator(githubRefName = "main", versionFile = null).computeVersion()
        }
    }

    @Test
    fun `a malformed VERSION file is rejected`() {
        assertFailsWith<GradleException> {
            calculator(githubRefName = "main", versionFile = "5").computeVersion()
        }
    }
}
