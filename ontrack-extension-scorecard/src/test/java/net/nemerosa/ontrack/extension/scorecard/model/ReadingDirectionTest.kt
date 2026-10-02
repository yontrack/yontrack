package net.nemerosa.ontrack.extension.scorecard.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadingDirectionTest {

    @Test
    fun `Lower is better, the target is met at or under it`() {
        assertEquals(true, ReadingDirection.LOWER_IS_BETTER.met(3600.0, 7200.0))
        assertEquals(true, ReadingDirection.LOWER_IS_BETTER.met(7200.0, 7200.0))
        assertEquals(false, ReadingDirection.LOWER_IS_BETTER.met(7201.0, 7200.0))
    }

    @Test
    fun `Higher is better, the target is met at or over it`() {
        assertEquals(true, ReadingDirection.HIGHER_IS_BETTER.met(95.0, 90.0))
        assertEquals(true, ReadingDirection.HIGHER_IS_BETTER.met(90.0, 90.0))
        assertEquals(false, ReadingDirection.HIGHER_IS_BETTER.met(89.9, 90.0))
    }

    @Test
    fun `No target or no value, not judged`() {
        ReadingDirection.entries.forEach { direction ->
            assertNull(direction.met(1.0, null))
            assertNull(direction.met(null, 1.0))
            assertNull(direction.met(null, null))
        }
    }

    @Test
    fun `Direction fixed by each reading of the catalogue`() {
        assertEquals(
            mapOf(
                ReadingKeys.DELIVERY_LEAD_TIME to ReadingDirection.LOWER_IS_BETTER,
                ReadingKeys.DELIVERY_FREQUENCY to ReadingDirection.HIGHER_IS_BETTER,
                ReadingKeys.DELIVERY_SUCCESS_RATE to ReadingDirection.HIGHER_IS_BETTER,
                ReadingKeys.DELIVERY_MTTR to ReadingDirection.LOWER_IS_BETTER,
                ReadingKeys.QUALITY_TEST_PASS_RATE to ReadingDirection.HIGHER_IS_BETTER,
                ReadingKeys.QUALITY_TEST_FLAKINESS to ReadingDirection.LOWER_IS_BETTER,
                ReadingKeys.SECURITY_MATURITY to ReadingDirection.HIGHER_IS_BETTER,
                ReadingKeys.SECURITY_REMEDIATION_TIME to ReadingDirection.LOWER_IS_BETTER,
                ReadingKeys.SECURITY_OVERDUE to ReadingDirection.LOWER_IS_BETTER,
            ),
            ReadingKeys.ORDER.associateWith { ReadingKeys.direction(it) }
        )
        assertNull(ReadingKeys.direction("test.unknown"))
    }
}
