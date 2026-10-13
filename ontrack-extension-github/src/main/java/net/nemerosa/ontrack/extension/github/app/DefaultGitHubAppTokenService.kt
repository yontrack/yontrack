package net.nemerosa.ontrack.extension.github.app

import net.nemerosa.ontrack.extension.github.app.client.GitHubAppAccount
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppClient
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

@Service
class DefaultGitHubAppTokenService(
    private val gitHubAppClient: GitHubAppClient,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : GitHubAppTokenService {

    /**
     * Using an in-memory cache, indexed by the configuration name.
     *
     * Each token is stored with a fingerprint of the configuration it was generated for, so that
     * an edited configuration gets a new token at once, on every replica.
     */
    private val cache = ConcurrentHashMap<String, CachedToken>()

    override fun invalidateAppInstallationToken(configurationName: String) {
        cache.remove(configurationName)
    }

    override fun getAppInstallationToken(
        configurationName: String,
        appId: String,
        appPrivateKey: String,
        appInstallationAccountName: String?
    ): String? = gitHubAppToken(configurationName, appId, appPrivateKey, appInstallationAccountName)?.token

    override fun getAppInstallationTokenInformation(
        configurationName: String,
        appId: String,
        appPrivateKey: String,
        appInstallationAccountName: String?
    ): GitHubAppToken? = gitHubAppToken(configurationName, appId, appPrivateKey, appInstallationAccountName)

    override fun getAppInstallationAccount(
        configurationName: String,
        appId: String,
        appPrivateKey: String,
        appInstallationAccountName: String?,
    ): GitHubAppAccount? =
        gitHubAppToken(configurationName, appId, appPrivateKey, appInstallationAccountName)?.installation?.account

    private fun gitHubAppToken(
        configurationName: String,
        appId: String,
        appPrivateKey: String,
        appInstallationAccountName: String?
    ): GitHubAppToken? {
        val fingerprint = fingerprint(appId, appPrivateKey, appInstallationAccountName)
        return cache.compute(configurationName) { _, existing: CachedToken? ->
            existing
                // If token is defined, checks it belongs to the same configuration and is still valid
                ?.takeIf { it.fingerprint == fingerprint && it.token.isValid() }
                // If not defined, edited or not valid, regenerate it
                ?: renewToken(
                    appId,
                    appPrivateKey,
                    appInstallationAccountName
                )?.let { CachedToken(fingerprint, it) }
        }?.token
    }

    private fun fingerprint(
        appId: String,
        appPrivateKey: String,
        appInstallationAccountName: String?
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(appId, appPrivateKey, appInstallationAccountName ?: "").forEach {
            digest.update(it.toByteArray())
            digest.update(0)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun renewToken(
        appId: String,
        appPrivateKey: String,
        appInstallationAccountName: String?
    ): GitHubAppToken? {
        // If not checking, we don't return any token
        if (!ontrackConfigProperties.configurationTest) {
            return null
        }
        // Gets a client
        val appClient = GitHubApp(gitHubAppClient)
        // Generate JWT token
        val jwt = GitHubApp.generateJWT(appId, appPrivateKey)
        // Getting the installation
        val appInstallationId = appClient.getInstallation(jwt, appId, appInstallationAccountName)
        // Generating & storing the token
        return appClient.generateInstallationToken(jwt, appInstallationId)
    }

    /**
     * Token, with the fingerprint of the configuration it was generated for.
     */
    private class CachedToken(
        val fingerprint: String,
        val token: GitHubAppToken,
    )
}
