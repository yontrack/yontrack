package net.nemerosa.ontrack.build

/**
 * What goes into the Spring Boot `build-info.properties` of the application jar, read back by
 * `EnvServiceImpl` as `VersionInfo`.
 *
 * A release re-tags the rc image and never rebuilds it (docs/adr/0006), so the running
 * application can never learn the version it was released under. That version therefore has
 * to be the one stamped at build time: [version] - what both UIs display - is the base version
 * (`5.4.0`), which is exactly what the rc is a candidate for. The rc identity (`5.4.0-rc-142`),
 * computed by the CI shell after Gradle has computed `project.version`, is kept aside as `full`.
 *
 * @property version Build-info version, read back as `VersionInfo.display`
 * @property additional Additional build-info properties: `full`, `branch`, `build` and `commit`
 */
data class BuildInfoVersion(
    val version: String,
    val additional: Map<String, String>,
) {
    companion object {

        private const val NOT_AVAILABLE = "n/a"

        /**
         * @param projectVersion `project.version`: the base version, or a `-dev` one locally
         * @param version `VERSION` exported by CI - the rc version on `main` and `release/X.Y`
         * @param branch `GITHUB_REF_NAME`
         * @param build `GITHUB_RUN_NUMBER`
         * @param commit `GITHUB_SHA`
         */
        fun compute(
            projectVersion: String,
            version: String?,
            branch: String?,
            build: String?,
            commit: String?,
        ) = BuildInfoVersion(
            version = projectVersion,
            additional = mapOf(
                "full" to (version.orNullIfBlank() ?: projectVersion),
                "branch" to (branch.orNullIfBlank() ?: NOT_AVAILABLE),
                "build" to (build.orNullIfBlank() ?: NOT_AVAILABLE),
                "commit" to (commit.orNullIfBlank() ?: NOT_AVAILABLE),
            ),
        )

        private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
    }
}
