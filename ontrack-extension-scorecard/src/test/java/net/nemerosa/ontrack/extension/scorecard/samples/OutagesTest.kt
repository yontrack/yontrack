package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class OutagesTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 0, 0)
    private val interval = Interval(ref, ref.plusDays(30))

    private var nextId = 1

    /**
     * Build created on [day] of the interval, promoted on [promoted] if set
     */
    private fun build(day: Long, promoted: Long? = null, branchId: Int = 1, id: Int = nextId++) = BuildSample(
        branchId = branchId,
        buildId = id,
        creation = ref.plusDays(day),
        promotion = promoted?.let { ref.plusDays(it) },
    )

    @Test
    fun `B1 promoted, B2 to B4 unpromoted, B5 promoted - from the creation of B2 to the promotion of B5`() {
        val b1 = build(1, promoted = 2)
        val b2 = build(3)
        val b3 = build(4)
        val b4 = build(5)
        val b5 = build(6, promoted = 8)
        val outages = Outages.of(listOf(b1, b2, b3, b4, b5), interval)
        assertEquals(listOf(OutageSample(1, b2.creation, b5.promotion)), outages)
        assertEquals(5 * 86400.0, outages.single().timeToRestore!!.seconds)
    }

    @Test
    fun `Builds taken in the order of their creation`() {
        val builds = listOf(
            build(6, promoted = 8),
            build(4),
            build(1, promoted = 2),
            build(5),
            build(3),
        )
        assertEquals(
            listOf(OutageSample(1, ref.plusDays(3), ref.plusDays(8))),
            Outages.of(builds, interval)
        )
    }

    @Test
    fun `Builds created at the same time taken in the order of their ID`() {
        val builds = listOf(
            build(3, promoted = 4, id = 12),
            build(3, id = 11),
            build(1, promoted = 2, id = 10),
        )
        assertEquals(
            listOf(OutageSample(1, ref.plusDays(3), ref.plusDays(4))),
            Outages.of(builds, interval)
        )
    }

    @Test
    fun `Every build promoted - no outage`() {
        assertEquals(
            emptyList(),
            Outages.of(listOf(build(1, promoted = 1), build(2, promoted = 3), build(4, promoted = 4)), interval)
        )
    }

    @Test
    fun `Unpromoted builds before the first promoted one are not an outage`() {
        assertEquals(
            emptyList(),
            Outages.of(listOf(build(1), build(2), build(3, promoted = 4)), interval)
        )
    }

    @Test
    fun `Unpromoted builds after the last promoted one are an outage still going on`() {
        assertEquals(
            listOf(OutageSample(1, ref.plusDays(3), null)),
            Outages.of(listOf(build(1, promoted = 2), build(3), build(4)), interval)
        )
    }

    @Test
    fun `A promotion after the end of the interval is not known yet`() {
        assertEquals(
            listOf(OutageSample(1, ref.plusDays(3), null)),
            Outages.of(listOf(build(1, promoted = 2), build(3), build(4, promoted = 31)), interval)
        )
    }

    @Test
    fun `Builds created after the end of the interval are ignored`() {
        assertEquals(
            emptyList(),
            Outages.of(listOf(build(1, promoted = 2), build(31)), interval)
        )
    }

    @Test
    fun `Outage started before the interval and restored in it`() {
        assertEquals(
            listOf(OutageSample(1, ref.minusDays(10), ref.plusDays(2))),
            Outages.of(listOf(build(-20, promoted = -19), build(-10), build(1, promoted = 2)), interval)
        )
    }

    @Test
    fun `Outage restored before the interval is not kept`() {
        assertEquals(
            emptyList(),
            Outages.of(listOf(build(-20, promoted = -19), build(-10), build(-5, promoted = -4)), interval)
        )
    }

    @Test
    fun `Several outages on one branch`() {
        val builds = listOf(
            build(1, promoted = 1),
            build(2),
            build(3, promoted = 4),
            build(5, promoted = 5),
            build(6),
            build(7),
            build(8, promoted = 10),
        )
        assertEquals(
            listOf(
                OutageSample(1, ref.plusDays(2), ref.plusDays(4)),
                OutageSample(1, ref.plusDays(6), ref.plusDays(10)),
            ),
            Outages.of(builds, interval)
        )
    }

    @Test
    fun `Outages read per branch`() {
        val builds = listOf(
            build(1, promoted = 1, branchId = 1),
            build(2, promoted = 2, branchId = 2),
            // Would close an outage of branch 1 if the branches were mixed
            build(3, branchId = 1),
            build(4, promoted = 5, branchId = 2),
            build(6, branchId = 2),
            build(7, promoted = 9, branchId = 2),
        )
        assertEquals(
            listOf(
                OutageSample(1, ref.plusDays(3), null),
                OutageSample(2, ref.plusDays(6), ref.plusDays(9)),
            ),
            Outages.of(builds, interval)
        )
    }
}
