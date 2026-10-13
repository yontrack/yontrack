package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Static configuration for the Git extension.
 */
@Component
@ConfigurationProperties(prefix = "ontrack.config.extension.git")
@APIName("Git configuration")
@APIDescription("Configuration of the connections to Git.")
class GitConfigProperties {

    /**
     * Remote properties
     */
    var remote = GitRemoteConfigProperties()

    /**
     * Pull requests configuration
     */
    var pullRequests = GitPullRequestConfigProperties()

    /**
     * Indexation configuration
     */
    var indexation = GitIndexationConfigProperties()

    /**
     * Indexation properties
     */
    class GitIndexationConfigProperties {

        @DurationUnit(ChronoUnit.MINUTES)
        @APIDescription("Timeout for the Git indexations (expressed by default in minutes)")
        var timeout: Duration = Duration.ofMinutes(30)

        /**
         * Cleanup job configuration
         */
        var cleanup = GitIndexationCleanupConfigProperties()
    }

    /**
     * Git indexation cleanup configuration properties
     */
    class GitIndexationCleanupConfigProperties {

        @APIDescription("Cleanup of Git indexations working directories: the clones of every replica are made again on their next use")
        var enabled = true

        @APIDescription("Cron for the job (empty to let it run manually)")
        var cron: String = ""

    }

    /**
     * General sync properties
     */
    class GitRemoteConfigProperties {

        @DurationUnit(ChronoUnit.SECONDS)
        @APIDescription(
            """
                Timeout (by default in seconds) for a given remote operation to start (like fetch & clone).
                Leave 0 to use the default system value. Set to 60 seconds by default.
                This timeout is used for the _connection_ part, not the total duration of the operation.
            """
        )
        var timeout: Duration = Duration.ofSeconds(60)

        @DurationUnit(ChronoUnit.SECONDS)
        @APIDescription(
            """
                Timeout (by default in minutes) for a given remote operation to _complete_ (like fetch & clone)
                
                Set to 10 minutes by default.
            """
        )
        var operationTimeout: Duration = Duration.ofMinutes(10)

        @APIDescription(
            """
                Number of retries to run when there is a timeout.
                
                Set to 0 for no retry.
            """
        )
        var retries: UInt = 3u

        @DurationUnit(ChronoUnit.SECONDS)
        @APIDescription(
            """
                Interval between retries (by default in seconds
                and set to 30 seconds by default).
            """
        )
        var interval: Duration = Duration.ofSeconds(30)

        @APIDescription(
            """
                Number of times we accept a "no remote" exception is thrown before deactivating the project in Ontrack.
                
                If <= 0, we always retry and never disable the project.
            """
        )
        var maxNoRemote: Int = 3

        @DurationUnit(ChronoUnit.SECONDS)
        @APIDescription(
            """
                How old (by default in seconds) the last fetch of a local clone can be when it is read.
                An older clone is fetched first. Each replica has clones of its own, and fetches them
                when it reads them: the indexation job, run on one replica, does not refresh the others.

                Set to 1 minute by default.
            """
        )
        var freshness: Duration = Duration.ofMinutes(1)

    }

    /**
     * Pull requests configuration
     */
    class GitPullRequestConfigProperties {

        @APIDescription("""Is the support for pull requests enabled?""")
        var enabled: Boolean = false

        @DurationUnit(ChronoUnit.SECONDS)
        @APIDescription("""Timeout before giving up on PR check""")
        var timeout: Duration = Duration.ofSeconds(5)

        /**
         * Pull requests caching configuration
         */
        var cache = GitPullRequestCacheConfigProperties()

        /**
         * Pull requests cleanup policy
         */
        var cleanup = GitPullRequestCleanupConfigProperties()
    }

    /**
     * Pull requests cleanup policy
     */
    class GitPullRequestCleanupConfigProperties {

        @APIDescription("Auto cleanup of pull requests")
        var enabled: Boolean = true

        @APIDescription("Days before disabling a PR branch after it's been closed or merged")
        var disabling: Int = 1

        @APIDescription("Days before deleting a PR branch after it's been closed or merged")
        var deleting: Int = 7
    }

    /**
     * Pull requests caching configuration
     */
    class GitPullRequestCacheConfigProperties {

        @APIDescription("Is the cache for pull requests enabled?")
        var enabled: Boolean = true

        @DurationUnit(ChronoUnit.MINUTES)
        @APIDescription(
            """
                Caching duration for pull requests. Time before a new connection is needed to get information
                about the PR from the SCM.
            """
        )
        var duration: Duration = Duration.ofHours(6)
    }

}