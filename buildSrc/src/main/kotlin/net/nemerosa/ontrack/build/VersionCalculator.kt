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

    fun computeVersion(): String {
        val currentBranch = currentBranch()
        return when {
            currentBranch == "main" -> computeMainVersion()
            currentBranch.startsWith("release/") -> computeReleaseVersion(currentBranch)
            else -> computeFeatureVersion(currentBranch)
        }
    }

    private fun readVersionFile(): String =
        versionFile()?.trim() ?: throw GradleException("VERSION file not found")

    private fun findLatestPatchVersion(baseVersion: String, qualifier: String = ""): Int {
        val pattern = if (qualifier.isNotEmpty()) {
            // Match tags like "5.0-alpha.1", "5.0-beta.2"
            Regex("^${Regex.escape(baseVersion)}-${Regex.escape(qualifier)}\\.(\\d+)$")
        } else {
            // Match tags like "5.0.1", "5.0.2"
            Regex("^${Regex.escape(baseVersion)}\\.(\\d+)$")
        }

        return gitTags()
            .mapNotNull { tag -> pattern.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: -1
    }

    private fun computeMainVersion(): String {
        val versionContent = readVersionFile()
        val versionPattern = Regex("^(\\d+\\.\\d+)(?:-(alpha|beta))?$")
        val matchResult = versionPattern.matchEntire(versionContent)
            ?: throw GradleException("VERSION file should contain version in format X.Y, X.Y-alpha, or X.Y-beta (e.g., 5.0, 5.0-alpha, 5.0-beta)")

        val baseVersion = matchResult.groupValues[1]
        val qualifier = matchResult.groupValues[2]

        val newPatch = findLatestPatchVersion(baseVersion, qualifier) + 1

        return if (qualifier.isNotEmpty()) {
            "$baseVersion-$qualifier.$newPatch"
        } else {
            "$baseVersion.$newPatch"
        }
    }

    private fun computeReleaseVersion(branchName: String): String {
        val versionFromBranch = branchName.removePrefix("release/")
        if (!versionFromBranch.matches(Regex("\\d+\\.\\d+"))) {
            throw GradleException("Release branch should be in format release/X.Y (e.g., release/5.0)")
        }
        val newPatch = findLatestPatchVersion(versionFromBranch) + 1
        return "$versionFromBranch.$newPatch"
    }

    private fun computeFeatureVersion(branchName: String): String {
        val targetVersion = readVersionFile()
        val commitHash = gitShortCommit()
        val sanitizedBranch = branchName.replace(Regex("[^a-zA-Z0-9._-]"), "-")
        return "$targetVersion-$sanitizedBranch-$commitHash"
    }
}
