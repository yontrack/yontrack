package net.nemerosa.ontrack.extension.stash.config

import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.config.model.EnvConstants
import net.nemerosa.ontrack.extension.stash.BitbucketServerFixtures

object BitbucketServerSCMEnvFixtures {
    /**
     * The project name is the caller's to choose and should be unique to the run, for the reason
     * given on [EnvFixtures.generic].
     */
    fun bitbucketServerEnv(projectName: String, extraEnv: Map<String, String> = emptyMap()) =
        EnvFixtures.generic(projectName) + mapOf(
            EnvConstants.GENERIC_SCM_URL to "${BitbucketServerFixtures.BITBUCKET_SERVER_URL}/scm/nemerosa/yontrack.git",
        ) + extraEnv
}
