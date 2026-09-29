package net.nemerosa.ontrack.kdsl.spec.extension.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.kdsl.spec.Configuration

/**
 * Configuration for using GitHub in Ontrack.
 *
 * @property password Deprecated: GitHub refuses passwords for its API, use a token ([oauth2Token]) or a GitHub App ([appId]).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubConfiguration(
    override val name: String,
    val url: String?,
    val user: String? = null,
    @Deprecated("Removed in V7. Use a token or a GitHub App instead. See #1923")
    val password: String? = null,
    val oauth2Token: String? = null,
    val appId: String? = null,
    val appPrivateKey: String? = null,
    val appInstallationAccountName: String? = null,
    val autoMergeToken: String? = null,
) : Configuration
