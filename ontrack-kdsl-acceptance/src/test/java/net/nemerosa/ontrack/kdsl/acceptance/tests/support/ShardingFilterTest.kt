package net.nemerosa.ontrack.kdsl.acceptance.tests.support

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.ShardingFilter.Companion.assignShard
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShardingFilterTest {

    private val classes = listOf("ACCDelta", "ACCAlpha", "ACCCharlie", "ACCBravo", "ACCEcho")

    @Test
    fun `no sharding returns everything`() {
        assertEquals(classes.toSet(), assignShard(classes, total = 1, index = 1))
        assertEquals(classes.toSet(), assignShard(classes, total = 0, index = 1))
    }

    @Test
    fun `the shards partition the classes`() {
        val total = 2
        val shards = (1..total).map { assignShard(classes, total = total, index = it) }
        assertEquals(classes.toSet(), shards.reduce { a, b -> a + b }, "Every class is assigned")
        assertEquals(classes.size, shards.sumOf { it.size }, "No class is assigned twice")
    }

    @Test
    fun `the shards are balanced whatever the number of shards`() {
        (2..5).forEach { total ->
            val sizes = (1..total).map { assignShard(classes, total = total, index = it).size }
            assertTrue(
                sizes.max() - sizes.min() <= 1,
                "Shards of $total differ by at most one class, got $sizes",
            )
        }
    }

    @Test
    fun `neighbours in the sorted order land on different shards`() {
        // Names cluster by feature and so does the cost, so alternating neighbours is what spreads
        // an expensive family evenly. Alpha and Bravo are neighbours once sorted.
        val first = assignShard(classes, total = 2, index = 1)
        val second = assignShard(classes, total = 2, index = 2)
        assertTrue("ACCAlpha" in first)
        assertTrue("ACCBravo" in second)
        assertTrue("ACCCharlie" in first)
    }

    @Test
    fun `the assignment does not depend on the order the classes are discovered in`() {
        assertEquals(
            assignShard(classes, total = 2, index = 1),
            assignShard(classes.shuffled(), total = 2, index = 1),
        )
    }
}
