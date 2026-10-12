package net.nemerosa.ontrack.build

import org.gradle.api.GradleException

/**
 * Computes the project version from the branch, the VERSION file and the git tags.
 *
 * Kept free of any Gradle [org.gradle.api.Project] so it can be unit tested: the caller
 * supplies the git and file lookups.
 */
internal class VersionCalculator(
    private val githubRefName: String?,
    private val gitBranch: () -> String,
    private val versionFile: () -> String?,
    private val gitTags: () -> List<String>,
    private val gitShortCommit: () -> String,
) {

    /**
     * `git rev-parse --abbrev-ref HEAD` answers the literal `HEAD` under the detached checkout
     * GitHub Actions performs, which would send `main` and the release branches down the feature
     * versioning path. `GITHUB_REF_NAME` carries the real branch name, so it wins when set.
     */
    fun currentBranch(): String =
        githubRefName?.takeIf { it.isNotBlank() }?.trim() ?: gitBranch()

    /**
     * The version Gradle builds and tags the Docker images with: `X.Y.Z` or `X.Y.0-stage.N` on
     * `main`, a major branch and a release branch, `X.Y.0-<branch>-<sha>` on a feature branch.
     * Valid SemVer 2.0.0 and a valid Docker tag in every case (docs/adr/0020).
     */
    fun computeVersion(): String {
        val currentBranch = currentBranch()
        return when {
            currentBranch == "main" || MAJOR_BRANCH.matches(currentBranch) -> computeMainVersion()
            currentBranch.startsWith("release/") -> computeReleaseVersion(currentBranch)
            else -> computeFeatureVersion(currentBranch)
        }
    }

    /**
     * The version CI pushes to GHCR and registers in Yontrack: on `main`, a major branch and a
     * release branch, every build is a release candidate for [computeVersion] and carries the run
     * number as `rc.<run>` (see [rcVersion]); a feature branch version is already unique.
     *
     * @param runNumber `GITHUB_RUN_NUMBER`, required on a candidate branch
     */
    fun computeCandidateVersion(runNumber: String?): String {
        val version = computeVersion()
        return if (isCandidateBranch(currentBranch())) {
            val run = runNumber?.trim()
            if (run == null || !RUN_NUMBER.matches(run)) {
                throw GradleException("A release candidate needs a run number (GITHUB_RUN_NUMBER), got '$runNumber'")
            }
            rcVersion(version, run)
        } else {
            version
        }
    }

    private fun isCandidateBranch(branch: String) =
        branch == "main" || MAJOR_BRANCH.matches(branch) || branch.startsWith("release/")

    private fun readVersionFile(): String =
        versionFile()?.trim() ?: throw GradleException("VERSION file not found")

    /**
     * The `VERSION` file names a line, not a version: `6.0`, `6.0-alpha` or `6.0-beta`.
     */
    private fun readVersionLine(): Pair<String, String> {
        val matchResult = VERSION_LINE.matchEntire(readVersionFile())
            ?: throw GradleException("VERSION file should contain version in format X.Y, X.Y-alpha, or X.Y-beta (e.g., 5.0, 5.0-alpha, 5.0-beta)")
        return matchResult.groupValues[1] to matchResult.groupValues[2]
    }

    private fun latestNumber(pattern: Regex): Int =
        gitTags()
            .mapNotNull { tag -> pattern.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: -1

    /** The highest patch of `X.Y`, from the `X.Y.Z` tags. */
    private fun findLatestPatchVersion(baseVersion: String): Int =
        latestNumber(Regex("^${Regex.escape(baseVersion)}\\.(\\d+)$"))

    /**
     * The highest number of a pre-release stage of `X.Y`, over the `X.Y.0-stage.N` tags and the
     * legacy `X.Y-stage.N` ones, which are published and never renamed - so the numbering carries
     * on across the change of shape: `6.0-beta.5` is followed by `6.0.0-beta.6`.
     */
    private fun findLatestPreReleaseNumber(baseVersion: String, qualifier: String): Int =
        latestNumber(Regex("^${Regex.escape(baseVersion)}(?:\\.0)?-${Regex.escape(qualifier)}\\.(\\d+)$"))

    private fun computeMainVersion(): String {
        val (baseVersion, qualifier) = readVersionLine()
        return if (qualifier.isNotEmpty()) {
            val number = findLatestPreReleaseNumber(baseVersion, qualifier) + 1
            "$baseVersion.0-$qualifier.$number"
        } else {
            val patch = findLatestPatchVersion(baseVersion) + 1
            "$baseVersion.$patch"
        }
    }

    private fun computeReleaseVersion(branchName: String): String {
        val versionFromBranch = branchName.removePrefix("release/")
        if (!RELEASE_LINE.matches(versionFromBranch)) {
            throw GradleException("Release branch should be in format release/X.Y (e.g., release/5.0)")
        }
        val newPatch = findLatestPatchVersion(versionFromBranch) + 1
        return "$versionFromBranch.$newPatch"
    }

    /**
     * `X.Y.0-<branch>-<sha>`: the stage of the `VERSION` file is dropped, and the branch is
     * reduced to one SemVer identifier - letters, digits and hyphens - then cut so that the whole
     * version fits in a Docker tag, with room left for the `.dev` of a local build (see
     * [devVersion]), whose `jibDockerBuild` tags an image too. The SHA is joined to it by a hyphen, so that a SHA made of
     * digits only is never a numeric identifier of its own.
     */
    private fun computeFeatureVersion(branchName: String): String {
        val (baseVersion, _) = readVersionLine()
        val prefix = "$baseVersion.0-"
        val suffix = "-${gitShortCommit()}"
        val branch = branchName
            .replace(Regex("[^0-9A-Za-z-]+"), "-")
            .trim('-')
            .take(MAX_DOCKER_TAG_LENGTH - DEV_SUFFIX.length - prefix.length - suffix.length)
            .trimEnd('-')
            .ifEmpty { "branch" }
        return "$prefix$branch$suffix"
    }

    companion object {
        /**
         * A Docker tag is at most 128 characters, which is also why no version carries `+build`
         * metadata: a Docker tag refuses `+`.
         */
        private const val MAX_DOCKER_TAG_LENGTH = 128

        private const val DEV_SUFFIX = ".dev"

        private val VERSION_LINE = Regex("^(\\d+\\.\\d+)(?:-(alpha|beta))?$")

        private val RELEASE_LINE = Regex("\\d+\\.\\d+")

        /** A SemVer numeric identifier: no leading zero. */
        private val RUN_NUMBER = Regex("0|[1-9]\\d*")

        /**
         * A release candidate: `rc.<run>` appended as two dot-separated identifiers, so that the
         * run number is numeric and compares as a number - `rc-1000` would sort below `rc-999`.
         * `-rc.<run>` on a version with no pre-release (`6.0.1-rc.700`), `.rc.<run>` on a
         * pre-release (`6.0.0-beta.6.rc.700`), which sorts above its pre-release: an accepted
         * wart, only `+build` metadata could put it below, and Docker tags refuse `+`.
         */
        fun rcVersion(version: String, run: String): String = "${version}${preReleaseSeparator(version)}rc.$run"

        /**
         * A local build (`CI` unset): `-dev`, or `.dev` on a version that already has a pre-release.
         */
        fun devVersion(version: String): String = "${version}${preReleaseSeparator(version)}dev"

        /** None of these versions carries build metadata, so any hyphen opens the pre-release. */
        private fun preReleaseSeparator(version: String) = if ('-' in version) "." else "-"

        /**
         * A `v<major>` branch is the long-lived development branch of the next major - `v7` while
         * `main` is still building 6.x, as `v6` was while it built 5.x. It is a second `main`, not
         * a feature branch: it carries its own `VERSION` file (`7.0`), it is built and promoted
         * like `main`, and it has a deployment environment of its own. So it takes the same
         * versioning path, which reads that `VERSION` and the `7.0.<n>` tags.
         *
         * Anchored and digits-only on purpose: `v7` is a major branch, `v7-spring-boot-5` is a
         * feature branch working towards it and must keep the feature version shape.
         *
         * See doc/dev-guide/major-branch.md.
         */
        private val MAJOR_BRANCH = Regex("v\\d+")
    }
}
