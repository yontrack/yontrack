package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.metrics.MetricsSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What the jobs fill is seen through every replica (#2078):
 *
 * - what is the same for the cluster (the connector statuses, the counts of the SCM catalog) is
 *   computed by a cluster job, which runs on one replica, and stored in the database;
 * - what is per pod by nature (the reachability of the evidence storage, the evidence gauges) is
 *   refreshed by a `REPLICA` job on every replica, which is not on the jobs page.
 */
class ACCHAReplicaJobs : AbstractACCHATestSupport() {

    @Test
    fun `the connector statuses collected by one backend are served by both`() {
        val before = listOf(ACCProperties.HA.A.url, ACCProperties.HA.B.url)
            .flatMap { jiraStatusTimes(it).values }
            .maxOrNull()
        val job = job(ontrackA, CORE, CONNECTOR_STATUS, JIRA)
        ontrackA.jobs.runNow(job.id)
        // The configurations of compose/casc/kdsl-ha.yaml
        val connectors = (1..5).map { "ha-startup-$it" }.toSet()
        waitUntil(timeout = 60_000L, interval = 1_000L, task = "The Jira statuses are collected again through A and B") {
            listOf(ACCProperties.HA.A.url, ACCProperties.HA.B.url).all { url ->
                val times = jiraStatusTimes(url)
                times.keys.containsAll(connectors) &&
                        connectors.all { connector -> before == null || times.getValue(connector) > before }
            }
        }
        assertEquals(
            jiraStatusTimes(ACCProperties.HA.A.url),
            jiraStatusTimes(ACCProperties.HA.B.url),
            "Same Jira statuses through A and through B"
        )
    }

    @Test
    fun `the evidence storage is probed by both backends`() {
        val initial = listOf(ACCProperties.HA.A.url, ACCProperties.HA.B.url).associateWith { storageCheckedAt(it) }
        // The storage is probed every minute on every replica
        waitUntil(timeout = 180_000L, interval = 5_000L, task = "The evidence storage is probed again by A and B") {
            initial.all { (url, checkedAt) -> storageCheckedAt(url) > checkedAt }
        }
        listOf(ACCProperties.HA.A.url, ACCProperties.HA.B.url).forEach { url ->
            assertEquals("OK", storageState(url), "Evidence storage reachable from $url")
        }
    }

    @Test
    fun `the SCM catalog gauges computed by one backend are reported by both`() {
        // An orphan project: no SCM catalog entry
        ontrackA.createProject(uid("p_"), "")
        val job = job(ontrackB, SCM, CATALOG_METRICS, CATALOG_METRICS)
        ontrackB.jobs.runNow(job.id)
        waitUntil(timeout = 60_000L, interval = 1_000L, task = "The SCM catalog gauges are reported by A and B") {
            val orphansA = orphans(ACCProperties.HA.A.Mgt.url)
            val orphansB = orphans(ACCProperties.HA.B.Mgt.url)
            orphansA != null && orphansA > 0 && orphansA == orphansB
        }
    }

    @Test
    fun `the cluster jobs filling the database are on the jobs page, the per-replica jobs are not`() {
        listOf(ontrackA, ontrackB).forEach { ontrack ->
            assertTrue(
                ontrack.jobs.list(CORE, CONNECTOR_STATUS).isNotEmpty(),
                "The connector status jobs are on the jobs page"
            )
            assertTrue(
                ontrack.jobs.list(SCM, CATALOG_METRICS).isNotEmpty(),
                "The SCM catalog metrics job is on the jobs page"
            )
            assertTrue(
                ontrack.jobs.list(AUDIT_TRAIL, "evidence-metrics").isEmpty(),
                "The evidence metrics job is not on the jobs page"
            )
            assertTrue(
                ontrack.jobs.list(AUDIT_TRAIL, "evidence-storage").isEmpty(),
                "The evidence storage probe is not on the jobs page"
            )
        }
    }

    private fun job(ontrack: Ontrack, category: String, type: String, key: String): JobStatus =
        ontrack.jobs.status(category, type, key) ?: fail("Job $category/$type/$key not found")

    /**
     * Time of collection of the status of each Jira configuration, as served by one backend
     */
    private fun jiraStatusTimes(url: String): Map<String, LocalDateTime> =
        graphQL(
            url,
            """
                {
                    systemHealth {
                        connectors {
                            statuses {
                                status { description { connector { type name } } }
                                time
                            }
                        }
                    }
                }
            """
        ).path("data").path("systemHealth").path("connectors").path("statuses")
            .filter { it.path("status").path("description").path("connector").path("type").asText() == JIRA }
            .associate {
                it.path("status").path("description").path("connector").path("name").asText() to
                        parseTime(it.path("time").asText())
            }

    private fun storageStatus(url: String) =
        graphQL(url, "{ auditTrailStatus { storage { state checkedAt } } }")
            .path("data").path("auditTrailStatus").path("storage")

    private fun storageCheckedAt(url: String): LocalDateTime = parseTime(storageStatus(url).path("checkedAt").asText())

    private fun storageState(url: String): String = storageStatus(url).path("state").asText()

    /**
     * Number of orphan projects reported by the SCM catalog gauge of one backend
     *
     * @param mgtUrl URL of the management endpoints of the backend
     */
    private fun orphans(mgtUrl: String): Int? =
        MetricsSupport.parseMetrics(DefaultConnector(mgtUrl, emptyMap()).get("/prometheus").body.asText())
            .list.find { it.name == "ontrack_extension_scm_catalog_orphan" }
            ?.getValue()

    private fun parseTime(text: String): LocalDateTime =
        if (text.endsWith("Z")) {
            LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC)
        } else {
            LocalDateTime.parse(text)
        }

    companion object {
        private const val CORE = "core"
        private const val CONNECTOR_STATUS = "connector-status"
        private const val JIRA = "jira"
        private const val SCM = "scm"
        private const val CATALOG_METRICS = "catalog-metrics"
        private const val AUDIT_TRAIL = "audit-trail"
    }
}
