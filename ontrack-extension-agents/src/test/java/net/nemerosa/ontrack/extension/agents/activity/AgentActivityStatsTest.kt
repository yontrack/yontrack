package net.nemerosa.ontrack.extension.agents.activity

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentActivityStatsTest {

    private fun stats(assisted: Int, known: Int, unknown: Int = 0) = AgentActivityStats(
        window = 30,
        from = LocalDateTime.of(2026, 9, 8, 10, 0),
        builds = 0,
        promotions = 0,
        deployments = 0,
        assistedBuilds = assisted,
        knownBuilds = known,
        unknownBuilds = unknown,
    )

    @Test
    fun `The assisted share is over the known builds only`() {
        assertEquals(0.25, stats(assisted = 1, known = 4, unknown = 12).assistedShare)
    }

    @Test
    fun `No assisted share without any known build`() {
        assertNull(stats(assisted = 0, known = 0, unknown = 3).assistedShare)
    }

    @Test
    fun `Every known build assisted`() {
        assertEquals(1.0, stats(assisted = 2, known = 2).assistedShare)
    }
}
