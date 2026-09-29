package net.nemerosa.ontrack.extension.github.model

import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface

/**
 * Reports a GitHub configuration authenticating with a user and a password, which V7 no longer
 * accepts: GitHub itself refuses passwords for its API, a token or a GitHub App is needed.
 */
fun DeprecationService.checkGitHubPasswordAuthentication(configuration: GitHubEngineConfiguration) {
    if (configuration.authenticationType == GitHubAuthenticationType.PASSWORD) {
        deprecatedUsage(
            surface = DeprecationSurface.SETTINGS,
            item = "GitHub configuration password authentication",
            message = "Removed in V7. Use a token or a GitHub App instead. See #1923",
        )
    }
}
