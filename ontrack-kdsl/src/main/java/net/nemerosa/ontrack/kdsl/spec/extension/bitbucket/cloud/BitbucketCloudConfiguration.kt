package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.kdsl.spec.Configuration

/**
 * Bitbucket Cloud configuration.
 *
 * @property name Name of the configuration
 * @property authType `API_TOKEN` (Atlassian account email + API token) or `ACCESS_TOKEN` (Bearer token)
 * @property email Atlassian account email, for `API_TOKEN`
 * @property token API token or access token
 * @property autoMergeEmail Atlassian account email of the identity approving pull requests
 * @property autoMergeToken API token of the identity approving pull requests
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudConfiguration(
    override val name: String,
    val authType: String = API_TOKEN,
    val email: String? = null,
    val token: String? = null,
    val autoMergeEmail: String? = null,
    val autoMergeToken: String? = null,
) : Configuration {
    companion object {
        const val API_TOKEN = "API_TOKEN"
        const val ACCESS_TOKEN = "ACCESS_TOKEN"
    }
}
