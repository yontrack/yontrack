package net.nemerosa.ontrack.extension.stash.model

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.support.SCMUrl
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.support.UserPasswordConfiguration

/**
 * @property name Name of this configuration
 * @property url Bitbucket URL
 * @property user User name
 * @property password User password
 * @property autoMergeToken Token used for approving pull requests for the auto merge operations
 */
class StashConfiguration(
    name: String,
    val url: String,
    user: String?,
    password: String?,
    @APILabel("Auto merge user")
    @APIDescription("Slug of the user approving pull requests for the auto merge operations")
    val autoMergeUser: String?,
    @APILabel("Auto merge token")
    @APIDescription("Token used for approving pull requests for the auto merge operations")
    val autoMergeToken: String?,
) : UserPasswordConfiguration<StashConfiguration>(name, user, password) {

    override fun obfuscate() = StashConfiguration(
        name = name,
        url = url,
        user = user,
        password = "",
        autoMergeUser = autoMergeUser,
        autoMergeToken = "",
    )

    override fun withPassword(password: String?): StashConfiguration {
        return StashConfiguration(
            name = name,
            url = url,
            user = user,
            password = password,
            autoMergeUser = autoMergeUser,
            autoMergeToken = autoMergeToken,
        )
    }

    /**
     * Checks if a given "git clone" URL is associated with this configuration.
     *
     * The URL belongs to this configuration when it names the same host, scheme and port aside - see
     * [SCMUrl]. This is an origin comparison, never a substring test: the name of this configuration is a
     * reference to a stored credential, and a URL on another host must not be able to select it.
     *
     * @param url URL to test (like `https://bitbucket.dev.yontrack.com/scm/nemerosa/ontrack.git`
     * or `ssh://git@bitbucket.dev.yontrack.com:7999/nemerosa/ontrack.git`)
     * @return `true` if the URL is associated with this configuration
     */
    fun matchesUrl(url: String): Boolean = SCMUrl.sameHost(this.url, url)

}
