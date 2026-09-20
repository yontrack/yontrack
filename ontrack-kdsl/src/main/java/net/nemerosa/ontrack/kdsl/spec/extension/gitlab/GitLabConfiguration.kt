package net.nemerosa.ontrack.kdsl.spec.extension.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.kdsl.spec.Configuration

/**
 * GitLab configuration, for gitlab.com or a self-managed instance.
 *
 * There is no user: a personal access token is the only credential GitLab's REST API takes.
 *
 * @property name Name of the configuration
 * @property url URL of the GitLab instance
 * @property token Personal access token, with the `api` scope
 * @property ignoreSslCertificate Accepts any SSL certificate, for a self-managed instance behind an internal CA
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabConfiguration(
    override val name: String,
    val url: String,
    val token: String? = null,
    val ignoreSslCertificate: Boolean = false,
) : Configuration
