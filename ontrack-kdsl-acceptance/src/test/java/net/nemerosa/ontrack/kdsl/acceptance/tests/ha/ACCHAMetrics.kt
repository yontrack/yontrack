package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.metrics.MetricCollection
import net.nemerosa.ontrack.kdsl.acceptance.tests.metrics.MetricsSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.github.GitHubConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.github.gitHub
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The metrics of each backend (#2088):
 *
 * - they are tagged by the backend reporting them, through `application_instance`, its host name;
 * - the rate-limit gauges of the GitHub configurations follow the configurations, whichever
 *   backend creates or deletes them, within the refresh period of a minute.
 */
class ACCHAMetrics : AbstractACCHATestSupport() {

    @Test
    fun `the two backends report different application_instance values`() {
        val instanceA = instances(metrics(ACCProperties.HA.A.Mgt.url))
        val instanceB = instances(metrics(ACCProperties.HA.B.Mgt.url))
        assertEquals(setOf("ontrack-a"), instanceA, "Metrics of A tagged by its host name")
        assertEquals(setOf("ontrack-b"), instanceB, "Metrics of B tagged by its host name")
        assertNotEquals(instanceA, instanceB)
    }

    @Test
    fun `a GitHub configuration created through A gets its rate-limit gauges on B`() {
        val name = uid("gh_")
        ontrackA.configurations.gitHub.create(
            GitHubConfiguration(
                name = name,
                // The backend itself, inside its container, which has no rate limit to give: the
                // gauges are registered all the same
                url = "http://localhost:8080",
                oauth2Token = "not-used",
            )
        )
        try {
            waitUntil(timeout = 120_000L, interval = 5_000L, task = "Rate-limit gauges of $name on A and B") {
                hasRateLimitGauge(ACCProperties.HA.A.Mgt.url, name) &&
                        hasRateLimitGauge(ACCProperties.HA.B.Mgt.url, name)
            }
        } finally {
            ontrackB.configurations.gitHub.delete(name)
        }
        waitUntil(timeout = 120_000L, interval = 5_000L, task = "No rate-limit gauges of $name on A and B") {
            !hasRateLimitGauge(ACCProperties.HA.A.Mgt.url, name) &&
                    !hasRateLimitGauge(ACCProperties.HA.B.Mgt.url, name)
        }
    }

    private fun hasRateLimitGauge(mgtUrl: String, configuration: String): Boolean =
        metrics(mgtUrl).list
            .filter { it.name == RATE_LIMIT_GAUGE }
            .flatMap { it.values }
            .any { value -> value.tags.any { it.name == "configuration" && it.value == configuration } }

    /**
     * Values of the `application_instance` tag of the metrics of Yontrack
     */
    private fun instances(metrics: MetricCollection): Set<String> {
        val values = metrics.list.flatMap { it.values }
            .filter { value -> value.tags.any { it.name == "application" && it.value == "ontrack" } }
        assertTrue(values.isNotEmpty(), "Metrics are reported")
        return values.map { value -> value.tags.find { it.name == "application_instance" }?.value }.toSet()
            .mapNotNull { it ?: fail("A metric is not tagged with application_instance") }
            .toSet()
    }

    /**
     * @param mgtUrl URL of the management endpoints of the backend
     */
    private fun metrics(mgtUrl: String): MetricCollection =
        MetricsSupport.parseMetrics(DefaultConnector(mgtUrl, emptyMap()).get("/prometheus").body.asText())

    companion object {
        private const val RATE_LIMIT_GAUGE = "ontrack_extension_github_ratelimit_core_limit"
    }
}
