package net.nemerosa.ontrack.kdsl.acceptance.tests.github

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties

/**
 * Testing if the environment is set for testing against GitHub.
 *
 * Used by [TestOnGitHub].
 */
@Suppress("unused")
class TestOnGitHubCondition {

    companion object {

        /**
         * Both values are needed. The organization says the environment is configured for GitHub;
         * the token says the calls will be *authenticated*. Checking only the organization let the
         * tests run against github.com anonymously, where the 60 requests/hour limit is per IP and
         * shared with everything else on the runner - a rate-limit 403 then failed builds for
         * reasons that had nothing to do with the change under test.
         */
        @JvmStatic
        fun isEnabled(organization: String?, token: String?): Boolean =
            !organization.isNullOrBlank() && !token.isNullOrBlank()

        @JvmStatic
        fun isTestOnGitHubEnabled(): Boolean =
            isEnabled(
                organization = System.getenv("ONTRACK_ACCEPTANCE_GITHUB_ORGANIZATION"),
                token = ACCProperties.GitHub.token,
            )
    }
}
