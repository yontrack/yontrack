package net.nemerosa.ontrack.extension.config.model

/**
 * Well-known environment variables at CI level.
 */
object EnvConstants {

    /**
     * Issue service identifier legacy environment variable (`serviceId//serviceName`)
     */
    @Deprecated("Removed in V7. Use YONTRACK_CI_SCM_ISSUES instead. See #1923")
    const val YONTRACK_LEGACY_SCM_ISSUES = "ONTRACK_SCM_ISSUES"

    /**
     * Issue service identifier environment variable (`serviceId//serviceName`)
     */
    const val YONTRACK_CI_SCM_ISSUES = "YONTRACK_CI_SCM_ISSUES"

    /**
     * Project name
     */
    const val GENERIC_PROJECT_NAME = "PROJECT_NAME"

    /**
     * Branch SCM name
     */
    const val GENERIC_BRANCH_NAME = "BRANCH_NAME"

    /**
     * Build number
     */
    const val GENERIC_BUILD_NUMBER = "BUILD_NUMBER"

    /**
     * Build version
     */
    const val GENERIC_BUILD_VERSION = "VERSION"

    /**
     * Build revision
     */
    const val GENERIC_BUILD_REVISION = "BUILD_REVISION"

    /**
     * SCM URL
     */
    const val GENERIC_SCM_URL = "SCM_URL"

    /**
     * Git URL
     */
    const val GIT_URL = "GIT_URL"

    /**
     * Git commit
     */
    const val GIT_COMMIT = "GIT_COMMIT"
}
