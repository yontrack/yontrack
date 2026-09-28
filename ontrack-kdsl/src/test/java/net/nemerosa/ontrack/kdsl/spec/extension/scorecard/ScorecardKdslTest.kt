package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.MarkerKind
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingBasis
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScorecardKdslTest {

    private val t0 = LocalDateTime.of(2026, 9, 28, 10, 0)

    @Test
    fun `A promotion marker is sent with its level only`() {
        val input = EstateMarker.Promotion("GOLD").toInput()
        assertEquals(MarkerKind.PROMOTION, input.kind)
        assertEquals(Optional.present("GOLD"), input.levelName)
        assertEquals(Optional.absent(), input.environment)
        assertEquals(Optional.absent(), input.qualifier)
    }

    @Test
    fun `An environment marker is sent with its environment and the default qualifier`() {
        val input = EstateMarker.Environment("production").toInput()
        assertEquals(MarkerKind.ENVIRONMENT, input.kind)
        assertEquals(Optional.absent(), input.levelName)
        assertEquals(Optional.present("production"), input.environment)
        assertEquals(Optional.present(""), input.qualifier)
    }

    @Test
    fun `A set with no reading is not computed`() {
        assertFalse(set().isComputedAfter(null))
        assertFalse(set().isComputedAfter(t0))
    }

    @Test
    fun `A set is computed after a moment when its latest reading is strictly after it`() {
        val set = set(t0, t0.plusSeconds(5))
        assertEquals(t0.plusSeconds(5), set.computedAt)
        assertTrue(set.isComputedAfter(null))
        assertTrue(set.isComputedAfter(t0))
        assertFalse(set.isComputedAfter(t0.plusSeconds(5)))
    }

    @Test
    fun `The set with no estate and the set of an estate are found in a scorecard`() {
        val scorecard = Scorecard(
            sets = listOf(
                ScorecardSet(name = "Estate", estate = "Estate", readings = emptyList()),
                ScorecardSet(name = "Project", estate = null, readings = emptyList()),
            )
        )
        assertEquals("Project", scorecard.noEstate.name)
        assertEquals("Estate", scorecard.estate("Estate")?.name)
        assertEquals(null, scorecard.estate("Other"))
    }

    private fun set(vararg computedAt: LocalDateTime) = ScorecardSet(
        name = "Project",
        estate = null,
        readings = computedAt.mapIndexed { index, time ->
            Reading(
                key = "reading.$index",
                day = time.toLocalDate().toString(),
                computedAt = time,
                windowStart = time.minusDays(90),
                windowEnd = time,
                value = null,
                basis = ReadingBasis.UNKNOWN,
                unknownReason = null,
                details = null,
                direction = null,
                target = null,
                targetMet = null,
            )
        },
    )
}
