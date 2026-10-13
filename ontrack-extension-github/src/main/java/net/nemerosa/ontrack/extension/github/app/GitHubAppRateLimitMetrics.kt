package net.nemerosa.ontrack.extension.github.app

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.github.GitHubConfigurationProperties
import net.nemerosa.ontrack.extension.github.client.GitHubRateLimit
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Exporting rate limits of the GitHub configurations as metrics.
 *
 * The gauges are in the meter registry of each replica: a job running every minute on every
 * replica ([JobScope.REPLICA], #2088) re-syncs them with the configurations stored in the database,
 * whichever replica created or deleted them, and reads the rate limits from GitHub - never on a
 * scrape. A gauge has no value when its rate limit cannot be read.
 */
@Component
class GitHubAppRateLimitMetrics(
    private val gitHubConfigurationProperties: GitHubConfigurationProperties,
    private val gitHubConfigurationService: GitHubConfigurationService,
    private val gitHubClientFactory: OntrackGitHubClientFactory,
    private val meterRegistry: MeterRegistry,
) : JobProvider, Job {

    private val logger: Logger = LoggerFactory.getLogger(GitHubAppRateLimitMetrics::class.java)

    /**
     * Last rate limit read for each configuration having gauges on this replica, by name, empty
     * when it could not be read.
     */
    private val rateLimits = ConcurrentHashMap<String, ReadRateLimit>()

    override fun getStartingJobs(): Collection<JobRegistration> =
        if (gitHubConfigurationProperties.metrics.enabled) {
            listOf(JobRegistration(this, Schedule.EVERY_MINUTE, JobScope.REPLICA))
        } else {
            emptyList()
        }

    override fun getKey(): JobKey = JOB_TYPE.getKey("gauges")

    override fun getDescription(): String = "Rate-limit gauges of the GitHub configurations"

    override fun isDisabled(): Boolean = false

    override fun getTask() = JobRun { listener ->
        val count = refresh()
        listener.message("Rate-limit gauges refreshed for $count GitHub configuration(s)")
    }

    /**
     * Registers the gauges of the new configurations, removes those of the deleted ones, and reads
     * the rate limits of all of them.
     *
     * @return Number of configurations
     */
    @Synchronized
    fun refresh(): Int {
        val configurations = gitHubConfigurationService.configurations
        val names = configurations.map { it.name }.toSet()
        // Deleted configurations
        rateLimits.keys.filter { it !in names }.forEach { name ->
            logger.info("Unregistering the rate-limit gauges of the GitHub configuration $name")
            gauges.forEach { metric -> unregisterMetric(name, metric) }
            rateLimits.remove(name)
        }
        // Rate limits of the configurations, new or not
        configurations.forEach { configuration ->
            val rateLimit = try {
                ReadRateLimit(gitHubClientFactory.create(configuration).getRateLimit())
            } catch (any: Exception) {
                logger.error("Cannot read the rate limit of the GitHub configuration ${configuration.name}", any)
                null
            }
            val new = !rateLimits.containsKey(configuration.name)
            rateLimits[configuration.name] = rateLimit ?: ReadRateLimit.NONE
            if (new) {
                logger.info("Registering the rate-limit gauges of the GitHub configuration ${configuration.name}")
                registerMetrics(configuration.name)
            }
        }
        return configurations.size
    }

    private fun registerMetrics(name: String) {
        registerMetric(name, GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_limit) { core.limit }
        registerMetric(name, GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_remaining) { core.remaining }
        registerMetric(name, GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_used) { core.used }
        registerMetric(name, GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_limit) { graphql.limit }
        registerMetric(name, GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_remaining) { graphql.remaining }
        registerMetric(name, GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_used) { graphql.used }
    }

    private fun registerMetric(
        name: String,
        metric: String,
        valueFn: GitHubRateLimit.() -> Int,
    ) {
        Gauge.builder(metric, rateLimits) { limits ->
            val read = limits[name]
            when {
                read == null || !read.read -> Double.NaN
                // No rate limit for this configuration
                read.rateLimit == null -> 0.0
                else -> read.rateLimit.valueFn().toDouble()
            }
        }.tag(TAG_CONFIGURATION, name).register(meterRegistry)
    }

    private fun unregisterMetric(name: String, metric: String) {
        meterRegistry.find(metric).tag(TAG_CONFIGURATION, name).gauges().forEach {
            meterRegistry.remove(it)
        }
    }

    /**
     * Rate limit read from GitHub, if [read] at all.
     */
    private class ReadRateLimit(
        val rateLimit: GitHubRateLimit?,
        val read: Boolean = true,
    ) {
        companion object {
            val NONE = ReadRateLimit(rateLimit = null, read = false)
        }
    }

    companion object {
        private const val TAG_CONFIGURATION = "configuration"

        /**
         * Names of the gauges of a configuration
         */
        internal val gauges = listOf(
            GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_limit,
            GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_remaining,
            GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_used,
            GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_limit,
            GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_remaining,
            GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_used,
        )

        private val JOB_TYPE: JobType = JobCategory.of("github").withName("GitHub")
            .getType("rate-limit-metrics").withName("Rate-limit metrics")
    }

}
