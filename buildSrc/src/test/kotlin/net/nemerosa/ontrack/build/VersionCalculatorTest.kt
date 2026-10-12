package net.nemerosa.ontrack.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
        assertEquals("5.3.0-beta.4", version)
    }

    // The first 6.0 alpha: only the 5.x releases are tagged, and none of them counts (#1987).
    @Test
    fun `main under GitHub Actions starts an alpha at zero`() {
        val version = calculator(
            githubRefName = "main",
            gitBranch = "HEAD",
            versionFile = "6.0-alpha",
            tags = listOf("5.5.6", "5.5.7", "5.0-beta.3"),
        ).computeVersion()
        assertEquals("6.0.0-alpha.0", version)
    }

    @Test
    fun `main under GitHub Actions continues an alpha from its last tag`() {
        val version = calculator(
            githubRefName = "main",
            gitBranch = "HEAD",
            versionFile = "6.0-alpha",
            tags = listOf("5.5.7", "6.0-alpha.0", "6.0-alpha.1"),
        ).computeVersion()
        assertEquals("6.0.0-alpha.2", version)
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
     * `5.3.1-rc.4` from being read as patches of 5.3. Pinned because a released `5.3.10` losing to
     * `5.3.2` would republish a version that already shipped.
     */
    @Test
    fun `a patch takes the highest patch number, not the last tag`() {
        val version = calculator(
            githubRefName = "release/5.3",
            gitBranch = "HEAD",
            versionFile = "5.4",
            tags = listOf("5.3.0", "5.3.10", "5.3.2", "5.3.1-rc-4", "5.3.1-rc.4", "5.31.0"),
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
        assertEquals("5.3.0-claude-github-actions-pipeline-deadbee", version)
    }

    // --- GITHUB_REF_NAME=v7 -> main versioning ---

    /**
     * The next-major development branch (`v7`) is a second `main`: its own VERSION file, its own
     * promotion chain, its own environment. So it versions like `main` - from the VERSION file it
     * carries and the tags of that major - and not like a feature branch.
     */
    @Test
    fun `a major branch under GitHub Actions versions like main`() {
        val version = calculator(
            githubRefName = "v7",
            gitBranch = "HEAD",
            versionFile = "7.0",
            tags = listOf("6.0.0", "6.0.1"),
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("7.0.0", version)
    }

    @Test
    fun `a major branch takes the next patch of its own major`() {
        val version = calculator(
            githubRefName = "v7",
            gitBranch = "HEAD",
            versionFile = "7.0",
            tags = listOf("7.0.0", "7.0.3", "6.0.9"),
        ).computeVersion()
        assertEquals("7.0.4", version)
    }

    /**
     * Digits only, and a full match: a branch working *towards* the next major is still a feature
     * branch, and must keep the feature version shape.
     */
    @Test
    fun `a branch named after a major but with a suffix is a feature branch`() {
        val version = calculator(
            githubRefName = "v7-spring-boot-5",
            gitBranch = "HEAD",
            versionFile = "7.0",
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("7.0.0-v7-spring-boot-5-deadbee", version)
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
        assertEquals("5.3.0-claude-github-actions-pipeline-deadbee", version)
    }

    @Test
    fun `a pull request ref produces a feature version`() {
        val version = calculator(
            githubRefName = "42/merge",
            gitBranch = "HEAD",
            versionFile = "5.3",
            shortCommit = "deadbee",
        ).computeVersion()
        assertEquals("5.3.0-42-merge-deadbee", version)
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

    // --- Pre-releases: X.Y.0-stage.N, numbered across the legacy X.Y-stage.N tags (#2068) ---

    /**
     * The legacy `6.0-beta.N` tags are published and never renamed, so the numbering carries on
     * from them: the first beta in the new shape follows the last legacy one.
     */
    @Test
    fun `a pre-release continues the numbering of the legacy tags`() {
        val version = calculator(
            githubRefName = "main",
            versionFile = "6.0-beta",
            tags = listOf("5.5.8", "6.0-alpha.3", "6.0-beta.4", "6.0-beta.5"),
        ).computeVersion()
        assertEquals("6.0.0-beta.6", version)
    }

    @Test
    fun `a pre-release counts the legacy and the new tags together`() {
        val version = calculator(
            githubRefName = "main",
            versionFile = "6.0-beta",
            tags = listOf("6.0-beta.5", "6.0-beta.6", "6.0.0-beta.7", "6.0.0-beta.8"),
        ).computeVersion()
        assertEquals("6.0.0-beta.9", version)
    }

    @Test
    fun `a pre-release ignores the candidates and the other stages`() {
        val version = calculator(
            githubRefName = "main",
            versionFile = "6.0-beta",
            tags = listOf("6.0.0-beta.2", "6.0.0-beta.9.rc.700", "6.0.0-alpha.12", "6.1.0-beta.30"),
        ).computeVersion()
        assertEquals("6.0.0-beta.3", version)
    }

    /**
     * The GA after the pre-releases: `6.0.0-beta.N` ends in a number too, and must not be read as
     * a patch of 6.0.
     */
    @Test
    fun `a GA after its pre-releases starts at patch zero`() {
        val version = calculator(
            githubRefName = "main",
            versionFile = "6.0",
            tags = listOf("6.0-beta.5", "6.0.0-beta.6", "6.0.0-beta.7"),
        ).computeVersion()
        assertEquals("6.0.0", version)
    }

    // --- Feature branches: X.Y.0-<branch>-<sha> ---

    @Test
    fun `a feature branch drops the stage of the VERSION file`() {
        val version = calculator(
            githubRefName = "claude/foo-pipeline",
            versionFile = "6.0-beta",
            shortCommit = "abc1234",
        ).computeVersion()
        assertEquals("6.0.0-claude-foo-pipeline-abc1234", version)
    }

    /**
     * `_` is illegal in a SemVer identifier and `.` would split the branch into several, so both
     * go, with `/` and anything else outside `[0-9A-Za-z-]`, and a run of them becomes one `-`.
     */
    @Test
    fun `a feature branch is reduced to letters, digits and hyphens`() {
        val version = calculator(
            githubRefName = "feature/foo_bar.baz//qux__1.2",
            versionFile = "6.0",
            shortCommit = "abc1234",
        ).computeVersion()
        assertEquals("6.0.0-feature-foo-bar-baz-qux-1-2-abc1234", version)
    }

    @Test
    fun `a feature branch loses the hyphens a reduction leaves at its ends`() {
        val version = calculator(
            githubRefName = "_wip_",
            versionFile = "6.0",
            shortCommit = "abc1234",
        ).computeVersion()
        assertEquals("6.0.0-wip-abc1234", version)
    }

    @Test
    fun `a feature branch with nothing left of its name is still a valid version`() {
        val version = calculator(
            githubRefName = "___",
            versionFile = "6.0",
            shortCommit = "0123456",
        ).computeVersion()
        assertEquals("6.0.0-branch-0123456", version)
    }

    /**
     * A short SHA made of digits only would be a numeric identifier on its own, where a leading
     * zero is illegal. Joined to the branch by a hyphen it is part of one alphanumeric identifier.
     */
    @Test
    fun `a feature branch with an all-digit short SHA`() {
        val version = calculator(
            githubRefName = "claude/foo-pipeline",
            versionFile = "6.0",
            shortCommit = "0123456",
        ).computeVersion()
        assertEquals("6.0.0-claude-foo-pipeline-0123456", version)
        assertSemanticVersion(version)
    }

    /**
     * A Docker tag is at most 128 characters, and a local build appends `.dev`: the branch is
     * cut, never the base version nor the SHA, which is what tells two builds of the branch apart.
     */
    @Test
    fun `a long feature branch is truncated to fit a Docker tag`() {
        val version = calculator(
            githubRefName = "claude/" + "very-long-description-".repeat(10) + "pipeline",
            versionFile = "6.0-beta",
            shortCommit = "abc1234",
        ).computeVersion()
        assertEquals(124, version.length)
        assertEquals(128, VersionCalculator.devVersion(version).length)
        assertTrue(version.startsWith("6.0.0-claude-very-long-description-"), version)
        assertTrue(version.endsWith("-abc1234"), version)
        assertTrue("--" !in version, version)
    }

    /**
     * Cutting at the limit can land just after a hyphen: the hyphen goes too, rather than
     * doubling the one before the SHA.
     */
    @Test
    fun `a truncated feature branch does not end with a hyphen`() {
        // 6.0.0- (6) + branch + -abc1234 (8) + .dev (4) = 128 -> 110 characters of branch, the
        // 110th a hyphen
        val branch = "a".repeat(109) + "-bcdef"
        val version = calculator(
            githubRefName = branch,
            versionFile = "6.0",
            shortCommit = "abc1234",
        ).computeVersion()
        assertEquals("6.0.0-" + "a".repeat(109) + "-abc1234", version)
    }

    // --- Candidates: rc.<run> as numeric identifiers ---

    @Test
    fun `a GA candidate on main`() {
        val version = calculator(
            githubRefName = "main",
            versionFile = "6.0",
            tags = listOf("6.0.0"),
        ).computeCandidateVersion(runNumber = "700")
        assertEquals("6.0.1-rc.700", version)
    }

    /**
     * Accepted wart: a pre-release candidate sorts above its pre-release. Only `+build` metadata
     * could place it below, and Docker tags refuse `+`.
     */
    @Test
    fun `a pre-release candidate on main`() {
        val version = calculator(
            githubRefName = "main",
            versionFile = "6.0-beta",
            tags = listOf("6.0-beta.5"),
        ).computeCandidateVersion(runNumber = "700")
        assertEquals("6.0.0-beta.6.rc.700", version)
    }

    @Test
    fun `a candidate on a release branch`() {
        val version = calculator(
            githubRefName = "release/6.0",
            versionFile = "6.1",
            tags = listOf("6.0.0", "6.0.1"),
        ).computeCandidateVersion(runNumber = "7")
        assertEquals("6.0.2-rc.7", version)
    }

    @Test
    fun `a candidate on a major branch`() {
        val version = calculator(
            githubRefName = "v7",
            versionFile = "7.0-alpha",
        ).computeCandidateVersion(runNumber = "12")
        assertEquals("7.0.0-alpha.0.rc.12", version)
    }

    /**
     * The point of `rc.<run>` over `rc-<run>`: a numeric identifier compares as a number, so the
     * thousandth run does not sort below the 999th.
     */
    @Test
    fun `the run number of a candidate is a numeric identifier of its own`() {
        assertEquals(listOf("rc", "1000"), VersionCalculator.rcVersion("6.0.1", "1000").substringAfter('-').split('.'))
        assertEquals(listOf("beta", "7", "rc", "1000"), VersionCalculator.rcVersion("6.0.0-beta.7", "1000").substringAfter('-').split('.'))
    }

    @Test
    fun `a feature branch is not a candidate`() {
        val version = calculator(
            githubRefName = "claude/foo-pipeline",
            versionFile = "6.0-beta",
            shortCommit = "abc1234",
        ).computeCandidateVersion(runNumber = "700")
        assertEquals("6.0.0-claude-foo-pipeline-abc1234", version)
    }

    @Test
    fun `a candidate needs a run number`() {
        assertFailsWith<GradleException> {
            calculator(githubRefName = "main", versionFile = "6.0").computeCandidateVersion(runNumber = null)
        }
        assertFailsWith<GradleException> {
            calculator(githubRefName = "main", versionFile = "6.0").computeCandidateVersion(runNumber = "07")
        }
    }

    // --- Local builds: -dev, or .dev on a pre-release ---

    @Test
    fun `a local GA build`() {
        assertEquals("6.0.1-dev", VersionCalculator.devVersion("6.0.1"))
    }

    @Test
    fun `a local pre-release build`() {
        assertEquals("6.0.0-beta.6.dev", VersionCalculator.devVersion("6.0.0-beta.6"))
    }

    @Test
    fun `a local feature branch build`() {
        assertEquals("6.0.0-claude-foo-pipeline-abc1234.dev", VersionCalculator.devVersion("6.0.0-claude-foo-pipeline-abc1234"))
    }

    // --- Every version is SemVer 2.0.0 and a Docker tag ---

    /**
     * Every shape the calculator, the CI candidate and the local build can produce, run against
     * the regex semver.org publishes and the one Docker applies to a tag.
     */
    @Test
    fun `every version is a semantic version and a Docker tag`() {
        val calculators = listOf(
            calculator(githubRefName = "main", versionFile = "6.0", tags = listOf("6.0.0", "6.0.9")),
            calculator(githubRefName = "main", versionFile = "6.0-alpha"),
            calculator(githubRefName = "main", versionFile = "6.0-beta", tags = listOf("6.0-beta.5")),
            calculator(githubRefName = "release/6.0", tags = listOf("6.0.3")),
            calculator(githubRefName = "v7", versionFile = "7.0"),
            calculator(githubRefName = "claude/foo_bar.baz-pipeline", versionFile = "6.0-beta", shortCommit = "0123456"),
            calculator(githubRefName = "42/merge", versionFile = "6.0"),
            calculator(githubRefName = "___", versionFile = "6.0", shortCommit = "0123456"),
            calculator(githubRefName = "x/" + "long-".repeat(40), versionFile = "6.0-beta"),
        )
        val versions = calculators.flatMap { calculator ->
            val version = calculator.computeVersion()
            listOf(
                version,
                calculator.computeCandidateVersion(runNumber = "1000"),
                VersionCalculator.devVersion(version),
            )
        }
        versions.forEach { assertSemanticVersion(it) }
        // A local build is never pushed, but its jibDockerBuild tags an image all the same
        versions.forEach { assertTrue(DOCKER_TAG.matches(it), "$it is not a Docker tag") }
    }

    private fun assertSemanticVersion(version: String) {
        assertTrue(SEMVER.matches(version), "$version is not a semantic version")
    }

    companion object {
        /** https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string */
        private val SEMVER = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
                "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
        )

        /** https://docs.docker.com/reference/cli/docker/image/tag/ */
        private val DOCKER_TAG = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$")
    }
}
