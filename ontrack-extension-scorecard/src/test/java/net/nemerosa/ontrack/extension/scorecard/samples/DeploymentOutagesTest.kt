package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeploymentOutagesTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 0, 0)
    private val interval = Interval(ref, ref.plusDays(30))

    private var nextNumber = 1

    /**
     * Deployment ended on [day] of the interval
     */
    private fun done(day: Long, branchId: Int = 1) = DeploymentSample(
        branchId = branchId,
        number = nextNumber++,
        end = ref.plusDays(day),
        failed = false,
    )

    private fun failed(day: Long, branchId: Int = 1) = DeploymentSample(
        branchId = branchId,
        number = nextNumber++,
        end = ref.plusDays(day),
        failed = true,
    )

    @Test
    fun `From a failed deployment to the next done one`() {
        val f = failed(3)
        val d = done(5)
        val outages = DeploymentOutages.of(listOf(done(1), f, d), interval)
        assertEquals(listOf(OutageSample(1, f.end, d.end)), outages)
        assertEquals(2 * 86400.0, outages.single().timeToRestore!!.seconds)
    }

    @Test
    fun `Consecutive failures make one outage, from the first of them`() {
        val f1 = failed(3)
        val f2 = failed(4)
        val d = done(6)
        val outages = DeploymentOutages.of(listOf(f1, f2, d), interval)
        assertEquals(listOf(OutageSample(1, f1.end, d.end)), outages)
    }

    @Test
    fun `A failure with no done deployment before it is an outage`() {
        val f = failed(1)
        val d = done(2)
        assertEquals(listOf(OutageSample(1, f.end, d.end)), DeploymentOutages.of(listOf(f, d), interval))
    }

    @Test
    fun `Deployments taken in the order of their end, their number breaking the ties`() {
        val d = done(5)
        val f = failed(3)
        assertEquals(listOf(OutageSample(1, f.end, d.end)), DeploymentOutages.of(listOf(d, f), interval))
    }

    @Test
    fun `A failure not restored is an outage still going on`() {
        val f = failed(20)
        assertEquals(listOf(OutageSample(1, f.end, null)), DeploymentOutages.of(listOf(done(1), f), interval))
    }

    @Test
    fun `An outage restored before the interval is not kept, one started before it and restored in it is`() {
        val before = Interval(ref.plusDays(10), ref.plusDays(30))
        val f1 = failed(2)
        val d1 = done(3)
        val f2 = failed(8)
        val d2 = done(12)
        assertEquals(
            listOf(OutageSample(1, f2.end, d2.end)),
            DeploymentOutages.of(listOf(f1, d1, f2, d2), before)
        )
    }

    @Test
    fun `A restoration after the end of the interval is not known yet`() {
        val f = failed(20)
        val d = done(40)
        assertEquals(listOf(OutageSample(1, f.end, null)), DeploymentOutages.of(listOf(f, d), interval))
    }

    @Test
    fun `No failure, no outage`() {
        assertTrue(DeploymentOutages.of(listOf(done(1), done(2)), interval).isEmpty())
    }
}
