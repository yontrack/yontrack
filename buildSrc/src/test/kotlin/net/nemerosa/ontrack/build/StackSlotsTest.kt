package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StackSlotsTest {

    @Test
    fun `slug of a plain directory name`() {
        assertEquals("yontrack", StackSlots.slug("/home/dev/yontrack"))
    }

    @Test
    fun `slug lowercases and collapses punctuation`() {
        assertEquals("my-work-tree", StackSlots.slug("/home/dev/My_Work.Tree"))
    }

    @Test
    fun `slug trims leading and trailing separators`() {
        assertEquals("5-0", StackSlots.slug("/home/dev/--5.0--"))
    }

    @Test
    fun `slug falls back when nothing usable is left`() {
        assertEquals("instance", StackSlots.slug("/home/dev/___"))
    }

    @Test
    fun `slot is stable for a given path`() {
        val path = "/home/dev/worktrees/feature-a"
        assertEquals(
            StackSlots.slotFromPath(path, ItStack.SLOT_MAX),
            StackSlots.slotFromPath(path, ItStack.SLOT_MAX),
        )
    }

    @Test
    fun `slot is always within the worktree range`() {
        listOf(ItStack.SLOT_MAX, KdslStack.SLOT_MAX).forEach { slotMax ->
            repeat(200) { i ->
                val slot = StackSlots.slotFromPath("/home/dev/worktrees/branch-$i", slotMax)
                assertTrue(slot in StackSlots.SLOT_MIN..slotMax, "slot $slot out of 1..$slotMax")
            }
        }
    }

    @Test
    fun `paths sharing a basename get different slots`() {
        // The hash is over the absolute path, not the directory name.
        val a = StackSlots.slotFromPath("/home/dev/one/5.0", ItStack.SLOT_MAX)
        val b = StackSlots.slotFromPath("/home/dev/two/5.0", ItStack.SLOT_MAX)
        assertNotEquals(a, b)
    }

    @Test
    fun `next slot wraps without ever returning zero`() {
        assertEquals(2, StackSlots.nextSlot(1, 9))
        assertEquals(StackSlots.SLOT_MIN, StackSlots.nextSlot(9, 9))
        assertEquals(StackSlots.SLOT_MIN, StackSlots.nextSlot(0, 9))
        assertEquals(StackSlots.SLOT_MIN, StackSlots.nextSlot(3, 3))
    }

    @Test
    fun `ports are offset by a hundred per slot`() {
        assertEquals(5432, StackSlots.port(5432, 0))
        assertEquals(5532, StackSlots.port(5432, 1))
        assertEquals(6332, StackSlots.port(5432, 9))
    }

    @Test
    fun `no two slots ever want the same port`() {
        // Otherwise a checkout on one slot would be told a port is busy
        // because another checkout publishes it as a different service, and
        // the slots would degrade to fewer usable instances than they claim.
        mapOf(
            "integration test" to (ItStack.BASE_PORTS to ItStack.SLOT_MAX),
            "KDSL acceptance" to (KdslStack.BASE_PORTS to KdslStack.SLOT_MAX),
        ).forEach { (stack, definition) ->
            val (basePorts, slotMax) = definition
            val seen = mutableMapOf<Int, Int>()
            (0..slotMax).forEach { slot ->
                StackSlots.ports(basePorts, slot).forEach { port ->
                    val other = seen.put(port, slot)
                    assertNull(other, "$stack: port $port is wanted by both slot $other and slot $slot")
                }
            }
        }
    }

    @Test
    fun `main checkout takes slot zero when the ports are free`() {
        assertEquals(0, resolve(mainCheckout = true) { true })
    }

    @Test
    fun `a worktree never takes slot zero`() {
        val slot = resolve(path = "/home/dev/worktrees/feature-a", mainCheckout = false) { true }
        assertTrue(slot in StackSlots.SLOT_MIN..ItStack.SLOT_MAX)
    }

    @Test
    fun `a busy slot is bumped along`() {
        // Slot 0 is fully taken -- a development stack in the same checkout,
        // typically -- so the main working copy moves to slot 1.
        val busy = StackSlots.ports(ItStack.BASE_PORTS, 0).toSet()
        assertEquals(1, resolve(mainCheckout = true) { it !in busy })
    }

    @Test
    fun `a single busy port is enough to move the slot`() {
        assertEquals(1, resolve(mainCheckout = true) { it != ItStack.BASE_VAULT })
    }

    @Test
    fun `a recorded slot is reused without probing the ports`() {
        val slot = resolve(mainCheckout = true, recordedSlot = 4) { error("must not probe a recorded slot") }
        assertEquals(4, slot)
    }

    @Test
    fun `no free slot at all is an error, named after the stack`() {
        val failure = runCatching { resolve(mainCheckout = true) { false } }.exceptionOrNull()
        assertTrue(failure?.message?.contains("No free slot for the integration test stack") == true, "got $failure")
    }

    @Test
    fun `every base port is probed`() {
        val probed = mutableListOf<Int>()
        resolve(mainCheckout = true) { probed += it; true }
        assertEquals(StackSlots.ports(ItStack.BASE_PORTS, 0), probed)
    }

    @Test
    fun `the recorded slot is read back under its own key`() {
        val dir = createTempDir()
        try {
            val file = File(dir, "instance.env")
            assertNull(StackSlots.readRecordedSlot(file, "IT_SLOT"))
            StackSlots.writeInstanceEnv(
                file = file,
                entries = mapOf("IT_SLOT" to "6", "PORT" to "1234"),
                systemProperties = mapOf("some.property" to "some value"),
            )
            assertEquals(6, StackSlots.readRecordedSlot(file, "IT_SLOT"))
            assertNull(StackSlots.readRecordedSlot(file, "KDSL_SLOT"))
            assertTrue(file.readText().contains("# -Dsome.property=some value"), file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun resolve(
        path: String = "/home/dev/yontrack",
        mainCheckout: Boolean,
        recordedSlot: Int? = null,
        portFree: (Int) -> Boolean,
    ): Int = StackSlots.resolveSlot(
        path = path,
        mainCheckout = mainCheckout,
        recordedSlot = recordedSlot,
        slotMax = ItStack.SLOT_MAX,
        basePorts = ItStack.BASE_PORTS,
        stackName = "integration test",
        portFree = portFree,
    )

    private fun createTempDir(): File =
        java.nio.file.Files.createTempDirectory("stack-slots-test").toFile()
}
