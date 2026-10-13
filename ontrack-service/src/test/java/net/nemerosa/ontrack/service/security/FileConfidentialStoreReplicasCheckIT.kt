package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.jobs.OtherReplica
import net.nemerosa.ontrack.service.job.JobSchedulerName
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `file` key store keeps a key per replica (#2086): a warning at startup lists the other live
 * replicas, as Quartz registers them. The integration tests run with the `file` store.
 *
 * Quartz writes its check-ins in transactions of its own: these tests are not transactional.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FileConfidentialStoreReplicasCheckIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var check: FileConfidentialStoreReplicasCheck

    @Autowired
    private lateinit var jobSchedulerName: JobSchedulerName

    @Autowired
    private lateinit var quartz: Scheduler

    @Test
    fun `another live replica is listed`() {
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.checkIn(jobSchedulerName.value)
            assertTrue(other.instance in check.otherReplicas(), "Live replica listed")
        }
    }

    @Test
    fun `this replica is not listed`() {
        assertFalse(quartz.schedulerInstanceId in check.otherReplicas(), "This replica not listed")
    }

    @Test
    fun `a replica which no longer checks in is not listed`() {
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.checkIn(
                jobSchedulerName.value,
                at = System.currentTimeMillis() - 2 * OtherReplica.CHECKIN_INTERVAL_MS,
            )
            assertFalse(other.instance in check.otherReplicas(), "Dead replica not listed")
        }
    }

    @Test
    fun `a replica of another installation is not listed`() {
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.checkIn("${jobSchedulerName.value}-other")
            assertFalse(other.instance in check.otherReplicas(), "Replica of another scheduler not listed")
        }
    }

    @Test
    fun `the warning names the other replicas`() {
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.checkIn(jobSchedulerName.value)
            val warning = check.warning()
            assertTrue(warning != null && other.instance in warning, "Warning names the other replica: $warning")
        }
    }

    @Test
    fun `no warning without another replica`() {
        assertNull(check.warning(), "No warning")
    }
}
