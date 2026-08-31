package net.nemerosa.ontrack.kdsl.acceptance.tests.github

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.extension.github.GitHubConfiguration

abstract class AbstractACCDSLGitHubTestSupport : AbstractACCDSLTestSupport() {

    /**
     * Creating a fake GitHub configuration.
     *
     * @param name Name for the configuration
     */
    protected fun fakeGitHubConfiguration(
        name: String = uid("GH"),
    ): GitHubConfiguration = GitHubConfiguration(
        name = name,
        url = null, // github.com by default
        // Without this the calls go to github.com anonymously and are rate-limited per runner
        // IP. TestOnGitHubCondition guarantees it is present whenever these tests run at all.
        oauth2Token = ACCProperties.GitHub.token,
    )

}