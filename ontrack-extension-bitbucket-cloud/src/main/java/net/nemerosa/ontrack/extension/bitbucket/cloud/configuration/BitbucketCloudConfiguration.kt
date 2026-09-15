package net.nemerosa.ontrack.extension.bitbucket.cloud.configuration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.support.CredentialsConfiguration

/**
 * Authentication to Bitbucket Cloud.
 */
enum class BitbucketCloudAuthType {
    /**
     * Atlassian account email + API token, using HTTP basic authentication. Available on every plan.
     */
    API_TOKEN,

    /**
     * Workspace, project or repository access token, used as a Bearer token.
     */
    ACCESS_TOKEN,
}

/**
 * Connection configuration to Bitbucket Cloud.
 *
 * The workspace is not part of the configuration but of the project property.
 *
 * @property name Name of this configuration
 * @property authType Type of authentication
 * @property email Atlassian account email, for [BitbucketCloudAuthType.API_TOKEN]
 * @property token API token or access token
 * @property autoMergeEmail Atlassian account email of the identity approving pull requests
 * @property autoMergeToken API token of the identity approving pull requests
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudConfiguration(
    @APIDescription("Name of the configuration")
    override val name: String,
    @APILabel("Authentication type")
    @APIDescription("Type of authentication: API_TOKEN (Atlassian account email + API token) or ACCESS_TOKEN (workspace, project or repository access token)")
    val authType: BitbucketCloudAuthType,
    @APIDescription("Atlassian account email, required for the API_TOKEN authentication type")
    val email: String? = null,
    @APIDescription("API token or access token")
    val token: String? = null,
    @APILabel("Auto merge email")
    @APIDescription("Atlassian account email of the identity approving pull requests for the auto merge operations")
    val autoMergeEmail: String? = null,
    @APILabel("Auto merge token")
    @APIDescription("API token of the identity approving pull requests for the auto merge operations")
    val autoMergeToken: String? = null,
) : CredentialsConfiguration<BitbucketCloudConfiguration> {

    override fun obfuscate() = copy(
        token = "",
        autoMergeToken = "",
    )

    override fun injectCredentials(oldConfig: BitbucketCloudConfiguration) = copy(
        token = if (token.isNullOrBlank() && authType == oldConfig.authType && email == oldConfig.email) {
            oldConfig.token
        } else {
            token
        },
        autoMergeToken = if (autoMergeToken.isNullOrBlank() && autoMergeEmail == oldConfig.autoMergeEmail) {
            oldConfig.autoMergeToken
        } else {
            autoMergeToken
        },
    )

    override fun encrypt(crypting: (plain: String?) -> String?) = copy(
        token = crypting(token?.takeIf { it.isNotBlank() }),
        autoMergeToken = crypting(autoMergeToken?.takeIf { it.isNotBlank() }),
    )

    override fun decrypt(decrypting: (encrypted: String?) -> String?) = copy(
        token = decrypting(token?.takeIf { it.isNotBlank() }),
        autoMergeToken = decrypting(autoMergeToken?.takeIf { it.isNotBlank() }),
    )

    /**
     * Checks the fields required by the authentication type.
     */
    fun checkFields() {
        if (authType == BitbucketCloudAuthType.API_TOKEN && email.isNullOrBlank()) {
            throw BitbucketCloudConfigurationMissingFieldException(name, "email")
        }
        if (token.isNullOrBlank()) {
            throw BitbucketCloudConfigurationMissingFieldException(name, "token")
        }
        if (!autoMergeToken.isNullOrBlank() && autoMergeEmail.isNullOrBlank()) {
            throw BitbucketCloudConfigurationMissingFieldException(name, "autoMergeEmail")
        }
    }

    override fun toString(): String = "BitbucketCloudConfiguration(name=$name, authType=$authType, email=$email)"
}

class BitbucketCloudConfigurationMissingFieldException(name: String, field: String) : InputException(
    "Bitbucket Cloud configuration %s: %s is required.",
    name,
    field,
)
