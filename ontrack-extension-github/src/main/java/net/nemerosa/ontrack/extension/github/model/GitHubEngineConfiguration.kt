package net.nemerosa.ontrack.extension.github.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.extension.github.app.GitHubApp
import net.nemerosa.ontrack.extension.scm.support.SCMUrl
import net.nemerosa.ontrack.model.annotations.APIIgnore
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import net.nemerosa.ontrack.model.docs.SelfDocumented
import net.nemerosa.ontrack.model.support.CredentialsConfiguration

/**
 * Configuration for accessing a GitHub engine, github.com or GitHub enterprise.
 *
 * @property name Name of this configuration
 * @property url End point
 * @property user User name
 * @property password User password
 * @property oauth2Token OAuth2 token
 * @property appId ID of the GitHub App to use for authentication
 * @property appPrivateKey GitHub App private key
 * @property appInstallationAccountName Account name of the GitHub App installation (used when more than 1 installation for the app)
 * @property autoMergeToken Token for an account used to approve pull requests for auto approval processes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SelfDocumented
open class GitHubEngineConfiguration(
    override val name: String,
    url: String?,
    val user: String? = null,
    val password: String? = null,
    @JsonAlias("token")
    val oauth2Token: String? = null,
    @JsonAlias("app-id")
    val appId: String? = null,
    @JsonAlias("app-private-key")
    val appPrivateKey: String? = null,
    @JsonAlias("app-installation")
    val appInstallationAccountName: String? = null,
    @JsonAlias("auto-merge-token")
    val autoMergeToken: String? = null,
) : CredentialsConfiguration<GitHubEngineConfiguration> {

    /**
     * Authentication type
     */
    @DocumentationIgnore
    @APIIgnore
    val authenticationType: GitHubAuthenticationType
        get() = when {
            !appId.isNullOrBlank() -> GitHubAuthenticationType.APP
            !oauth2Token.isNullOrBlank() -> when {
                user.isNullOrBlank() -> GitHubAuthenticationType.TOKEN
                else -> GitHubAuthenticationType.USER_TOKEN
            }

            !user.isNullOrBlank() -> GitHubAuthenticationType.PASSWORD
            else -> GitHubAuthenticationType.ANONYMOUS
        }

    /**
     * End point
     */
    @DocumentationIgnore
    val url: String = if (url.isNullOrBlank()) GITHUB_COM else url

    override fun obfuscate(): GitHubEngineConfiguration =
        GitHubEngineConfiguration(
            name = name,
            url = url,
            user = user,
            password = null,
            oauth2Token = null,
            appId = appId,
            appPrivateKey = null,
            appInstallationAccountName = appInstallationAccountName,
            autoMergeToken = null,
        )

    /**
     * We inject a credentials field (password, token or app private key) only
     * when:
     *
     * - the receiver (this) entry is not filled in
     * - no other receiver credentials entry is filled in (type == anonymous)
     */
    override fun injectCredentials(oldConfig: GitHubEngineConfiguration): GitHubEngineConfiguration {
        val authenticationType = this.authenticationType
        return GitHubEngineConfiguration(
            name = name,
            url = url,
            user = user,
            password = if (user != null && oldConfig.user == user && password.isNullOrBlank()) {
                oldConfig.password
            } else {
                password
            },
            oauth2Token = if (authenticationType == GitHubAuthenticationType.ANONYMOUS && oauth2Token.isNullOrBlank()) {
                oldConfig.oauth2Token
            } else {
                oauth2Token
            },
            appId = appId,
            appPrivateKey = if (!appId.isNullOrBlank() && appPrivateKey.isNullOrBlank()) {
                oldConfig.appPrivateKey
            } else {
                appPrivateKey
            },
            appInstallationAccountName = appInstallationAccountName,
            autoMergeToken = if (autoMergeToken.isNullOrBlank()) {
                oldConfig.autoMergeToken
            } else {
                autoMergeToken
            },
        )
    }

    override fun encrypt(crypting: (plain: String?) -> String?) = GitHubEngineConfiguration(
        name = name,
        url = url,
        user = user,
        password = crypting(password?.takeIf { it.isNotBlank() }),
        oauth2Token = crypting(oauth2Token?.takeIf { it.isNotBlank() }),
        appId = appId,
        appPrivateKey = crypting(appPrivateKey?.takeIf { it.isNotBlank() }),
        appInstallationAccountName = appInstallationAccountName,
        autoMergeToken = crypting(autoMergeToken?.takeIf { it.isNotBlank() }),
    )

    override fun decrypt(decrypting: (encrypted: String?) -> String?) = GitHubEngineConfiguration(
        name = name,
        url = url,
        user = user,
        password = decrypting(password?.takeIf { it.isNotBlank() }),
        oauth2Token = decrypting(oauth2Token?.takeIf { it.isNotBlank() }),
        appId = appId,
        appPrivateKey = decrypting(appPrivateKey?.takeIf { it.isNotBlank() }),
        appInstallationAccountName = appInstallationAccountName,
        autoMergeToken = decrypting(autoMergeToken?.takeIf { it.isNotBlank() }),
    )

    /**
     * Checks if a given "git clone" URL is associated with this configuration.
     *
     * The URL belongs to this configuration when it names the same host, scheme and port aside - see
     * [SCMUrl]. This is an origin comparison, never a substring test: the name of this configuration is a
     * reference to a stored credential, and a URL on another host must not be able to select it.
     *
     * @param url URL to test (like `https://github.dev.yontrack.com/scm/nemerosa/ontrack.git`
     * or `https://github.com/scm/nemerosa/ontrack.git`
     * or `git@github.com:nemerosa/ontrack.git`)
     * @return `true` if the URL is associated with this configuration
     */
    fun matchesUrl(url: String): Boolean = SCMUrl.sameHost(this.url, url)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GitHubEngineConfiguration

        if (name != other.name) return false
        if (user != other.user) return false
        if (password != other.password) return false
        if (oauth2Token != other.oauth2Token) return false
        if (appId != other.appId) return false
        if (appPrivateKey != other.appPrivateKey) return false
        if (appInstallationAccountName != other.appInstallationAccountName) return false
        if (url != other.url) return false
        if (autoMergeToken != other.autoMergeToken) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (oauth2Token?.hashCode() ?: 0)
        result = 31 * result + (appId?.hashCode() ?: 0)
        result = 31 * result + (appPrivateKey?.hashCode() ?: 0)
        result = 31 * result + (appInstallationAccountName?.hashCode() ?: 0)
        result = 31 * result + url.hashCode()
        result = 31 * result + (autoMergeToken?.hashCode() ?: 0)
        return result
    }

    fun checkFields() {
        // Password
        if (!password.isNullOrBlank() && user.isNullOrBlank()) {
            throw GitHubEngineConfigurationUserRequiredWithPasswordException()
        }
        if (!password.isNullOrBlank()) {
            if (!oauth2Token.isNullOrBlank()) {
                throw GitHubEngineConfigurationTokenMustBeVoidWithPasswordException()
            }
            if (!appId.isNullOrBlank() || !appPrivateKey.isNullOrBlank() || !appInstallationAccountName.isNullOrBlank()) {
                throw GitHubEngineConfigurationAppMustBeVoidWithPasswordException()
            }
        }
        // Token
        if (!oauth2Token.isNullOrBlank()) {
            if (!appId.isNullOrBlank() || !appPrivateKey.isNullOrBlank() || !appInstallationAccountName.isNullOrBlank()) {
                throw GitHubEngineConfigurationAppMustBeVoidWithTokenException()
            }
        }
        // App ID
        if (!appId.isNullOrBlank()) {
            if (appPrivateKey.isNullOrBlank()) {
                throw GitHubEngineConfigurationAppPrivateKeyRequiredException()
            }
        }
        // App key
        if (!appPrivateKey.isNullOrBlank()) {
            try {
                GitHubApp.readPrivateKey(appPrivateKey)
            } catch (any: Exception) {
                throw GitHubEngineConfigurationIncorrectAppPrivateKeyException(any.message ?: "")
            }
        }
    }

    companion object {

        /**
         * github.com end point.
         */
        const val GITHUB_COM = "https://github.com"

    }

}