package net.nemerosa.ontrack.extension.github.app

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.GitHubConfigurationProperties
import net.nemerosa.ontrack.extension.github.client.GitHubRateLimit
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.client.RateLimit
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.job.JobScope
import net.nemerosa.ontrack.job.Schedule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rate-limit gauges of the GitHub configurations follow the configurations, whichever replica
 * creates or deletes them: a job running every minute on every replica re-syncs them (#2088).
 */
internal class GitHubAppRateLimitMetricsTest {

    private lateinit var gitHubConfigurationProperties: GitHubConfigurationProperties
    private lateinit var gitHubConfigurationService: GitHubConfigurationService
    private lateinit var gitHubClientFactory: OntrackGitHubClientFactory
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var metrics: GitHubAppRateLimitMetrics

    /**
     * Configurations, as stored in the database, and their rate limits, as GitHub gives them
     */
    private val configurations = mutableMapOf<String, GitHubRateLimit?>()

    @BeforeEach
    fun init() {
        gitHubConfigurationProperties = GitHubConfigurationProperties()
        gitHubConfigurationService = mockk()
        every { gitHubConfigurationService.configurations } answers {
            configurations.keys.map { configuration(it) }
        }
        gitHubClientFactory = mockk()
        every { gitHubClientFactory.create(any()) } answers {
            val name = firstArg<GitHubEngineConfiguration>().name
            mockk<OntrackGitHubClient> {
                every { getRateLimit() } answers { configurations[name] }
            }
        }
        meterRegistry = SimpleMeterRegistry()
        metrics = GitHubAppRateLimitMetrics(
            gitHubConfigurationProperties,
            gitHubConfigurationService,
            gitHubClientFactory,
            meterRegistry,
        )
    }

    @Test
    fun `A job on every replica, every minute`() {
        val registration = metrics.startingJobs.single()
        assertEquals(JobScope.REPLICA, registration.scope)
        assertEquals(Schedule.EVERY_MINUTE, registration.schedule)
    }

    @Test
    fun `No job when the metrics are disabled`() {
        gitHubConfigurationProperties.metrics.enabled = false
        assertTrue(metrics.startingJobs.isEmpty())
    }

    @Test
    fun `Gauges of the configurations`() {
        configurations["one"] = rateLimit(core = 5000, graphql = 4000)
        configurations["two"] = rateLimit(core = 15000, graphql = 14000)
        metrics.refresh()
        assertEquals(5000.0, gauge(CORE_LIMIT, "one"))
        assertEquals(4000.0, gauge(GRAPHQL_LIMIT, "one"))
        assertEquals(15000.0, gauge(CORE_LIMIT, "two"))
        assertEquals(14000.0, gauge(GRAPHQL_LIMIT, "two"))
    }

    @Test
    fun `All the gauges of a configuration`() {
        configurations["one"] = GitHubRateLimit(
            core = RateLimit(limit = 5000, remaining = 4900, used = 100),
            graphql = RateLimit(limit = 4000, remaining = 3800, used = 200),
        )
        metrics.refresh()
        assertEquals(5000.0, gauge(CORE_LIMIT, "one"))
        assertEquals(4900.0, gauge(GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_remaining, "one"))
        assertEquals(100.0, gauge(GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_used, "one"))
        assertEquals(4000.0, gauge(GRAPHQL_LIMIT, "one"))
        assertEquals(3800.0, gauge(GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_remaining, "one"))
        assertEquals(200.0, gauge(GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_used, "one"))
    }

    @Test
    fun `A configuration created on another replica gets its gauges at the next refresh`() {
        configurations["one"] = rateLimit(core = 5000, graphql = 4000)
        metrics.refresh()
        configurations["two"] = rateLimit(core = 15000, graphql = 14000)
        assertNull(gauge(CORE_LIMIT, "two"), "No gauge before the refresh")
        metrics.refresh()
        assertEquals(15000.0, gauge(CORE_LIMIT, "two"))
    }

    @Test
    fun `A configuration deleted on another replica loses its gauges at the next refresh`() {
        configurations["one"] = rateLimit(core = 5000, graphql = 4000)
        configurations["two"] = rateLimit(core = 15000, graphql = 14000)
        metrics.refresh()
        configurations.remove("two")
        metrics.refresh()
        assertEquals(5000.0, gauge(CORE_LIMIT, "one"))
        GitHubAppRateLimitMetrics.gauges.forEach { name ->
            assertNull(gauge(name, "two"), "No $name gauge for the deleted configuration")
        }
    }

    @Test
    fun `The rate limits are read from GitHub at each refresh, not on each scrape`() {
        configurations["one"] = rateLimit(core = 5000, graphql = 4000)
        metrics.refresh()
        configurations["one"] = rateLimit(core = 6000, graphql = 4000)
        assertEquals(5000.0, gauge(CORE_LIMIT, "one"), "Same value until the next refresh")
        metrics.refresh()
        assertEquals(6000.0, gauge(CORE_LIMIT, "one"), "Value of the refresh")
    }

    @Test
    fun `A configuration without rate limits has gauges at 0`() {
        configurations["one"] = null
        metrics.refresh()
        assertEquals(0.0, gauge(CORE_LIMIT, "one"))
    }

    @Test
    fun `A configuration whose client cannot be created has gauges without value`() {
        configurations["one"] = rateLimit(core = 5000, graphql = 4000)
        every { gitHubClientFactory.create(match { it.name == "one" }) } throws IllegalStateException("No client")
        metrics.refresh()
        assertTrue(gauge(CORE_LIMIT, "one")?.isNaN() ?: false, "Gauge without value")
    }

    private fun gauge(name: String, configuration: String): Double? =
        meterRegistry.find(name).tag("configuration", configuration).gauge()?.value()

    private fun configuration(name: String) = GitHubEngineConfiguration(
        name = name,
        url = null,
        oauth2Token = "xxx",
    )

    private fun rateLimit(core: Int, graphql: Int) = GitHubRateLimit(
        core = RateLimit(limit = core, remaining = core, used = 0),
        graphql = RateLimit(limit = graphql, remaining = graphql, used = 0),
    )

    companion object {
        private const val CORE_LIMIT = GitHubAppRateLimitMetricsNames.Core.ontrack_extension_github_ratelimit_core_limit
        private const val GRAPHQL_LIMIT = GitHubAppRateLimitMetricsNames.GraphQL.ontrack_extension_github_ratelimit_graphql_limit
    }
}
