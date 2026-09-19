package net.nemerosa.ontrack.extension.gitlab.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.support.CredentialsConfiguration

/**
 * Configuration for accessing a GitLab instance, gitlab.com or self-managed.
 *
 * There is no user: GitLab's API has no password authentication, and a personal access token is the only
 * credential usable against the REST API - deploy tokens are barred from it and job tokens live only for the
 * duration of a job. HTTPS Git authentication uses the constant user name
 * [net.nemerosa.ontrack.extension.gitlab.property.GitLabGitConfiguration.GIT_USER].
 *
 * @property name Name of this configuration
 * @property url URL of the GitLab instance
 * @property token Personal access token, with the `api` scope
 * @property ignoreSslCertificate Accepts any SSL certificate, for a self-managed instance behind an internal CA
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabConfiguration(
    @APIDescription("Name of the configuration")
    override val name: String,
    @APILabel("GitLab URL")
    @APIDescription("URL of the GitLab instance, like https://gitlab.com")
    val url: String,
    @APIDescription("Personal access token used by Yontrack to connect to GitLab, with the `api` scope")
    val token: String? = null,
    @APILabel("Ignore SSL certificate")
    @APIDescription("Accepts any SSL certificate, for a self-managed instance behind an internal certificate authority")
    val ignoreSslCertificate: Boolean = false,
) : CredentialsConfiguration<GitLabConfiguration> {

    override fun obfuscate(): GitLabConfiguration = copy(token = "")

    override fun injectCredentials(oldConfig: GitLabConfiguration): GitLabConfiguration =
        if (token.isNullOrBlank()) {
            copy(token = oldConfig.token)
        } else {
            this
        }

    override fun encrypt(crypting: (plain: String?) -> String?): GitLabConfiguration =
        copy(token = crypting(token?.takeIf { it.isNotBlank() }))

    override fun decrypt(decrypting: (encrypted: String?) -> String?): GitLabConfiguration =
        copy(token = decrypting(token?.takeIf { it.isNotBlank() }))

    override fun toString(): String = "GitLabConfiguration(name=$name, url=$url)"
}
