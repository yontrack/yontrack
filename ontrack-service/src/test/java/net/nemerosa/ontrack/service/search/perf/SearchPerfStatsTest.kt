package net.nemerosa.ontrack.service.search.perf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SearchPerfStatsTest {

    @Test
    fun `p95 by the nearest rank`() {
        val samples = (1..100).map { it.toDouble() }.shuffled()
        assertEquals(95.0, SearchPerfStats.percentile(samples, 95.0))
        assertEquals(50.0, SearchPerfStats.percentile(samples, 50.0))
        assertEquals(100.0, SearchPerfStats.percentile(samples, 100.0))
    }

    @Test
    fun `percentile of a few samples is rounded up to a sample`() {
        assertEquals(30.0, SearchPerfStats.percentile(listOf(10.0, 30.0, 20.0), 95.0))
        assertEquals(10.0, SearchPerfStats.percentile(listOf(10.0), 95.0))
    }

    @Test
    fun `no percentile without samples`() {
        assertThrows<IllegalArgumentException> {
            SearchPerfStats.percentile(emptyList(), 95.0)
        }
    }

    @Test
    fun `summary of samples`() {
        val summary = SearchPerfStats.summary((1..200).map { it.toDouble() })
        assertEquals(200, summary.samples)
        assertEquals(100.0, summary.p50)
        assertEquals(190.0, summary.p95)
        assertEquals(200.0, summary.max)
    }
}
