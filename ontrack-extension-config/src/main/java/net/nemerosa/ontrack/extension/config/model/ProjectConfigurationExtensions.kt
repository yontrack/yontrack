package net.nemerosa.ontrack.extension.config.model

import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface

@Suppress("DEPRECATION")
fun ProjectConfiguration.getActualIssueServiceIdentifier(env: Map<String, String>): ProjectIssueServiceIdentifier? =
    issueServiceIdentifier
        ?: env.getEnv(
            EnvConstants.YONTRACK_CI_SCM_ISSUES,
            EnvConstants.YONTRACK_LEGACY_SCM_ISSUES,
        )
            ?.let { ProjectIssueServiceIdentifier.parse(it) }

/**
 * Whether the issue service identifier of this project is taken from the deprecated
 * [EnvConstants.YONTRACK_LEGACY_SCM_ISSUES] environment variable.
 */
@Suppress("DEPRECATION")
fun ProjectConfiguration.usesLegacyIssueServiceEnv(env: Map<String, String>): Boolean =
    issueServiceIdentifier == null &&
            env.getEnv(EnvConstants.YONTRACK_CI_SCM_ISSUES) == null &&
            env.getEnv(EnvConstants.YONTRACK_LEGACY_SCM_ISSUES) != null

/**
 * Reports the use of the deprecated [EnvConstants.YONTRACK_LEGACY_SCM_ISSUES] environment variable.
 */
fun DeprecationService.checkLegacyIssueServiceEnv(configuration: ProjectConfiguration, env: Map<String, String>) {
    if (configuration.usesLegacyIssueServiceEnv(env)) {
        deprecatedUsage(
            surface = DeprecationSurface.ENV,
            item = "ONTRACK_SCM_ISSUES",
            message = "Removed in V7. Use YONTRACK_CI_SCM_ISSUES instead. See #1923",
        )
    }
}
