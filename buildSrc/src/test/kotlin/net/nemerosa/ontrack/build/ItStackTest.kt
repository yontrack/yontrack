package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItStackTest {

    @Test
    fun `slug of a plain directory name`() {
        assertEquals("yontrack", ItStack.slug("/home/dev/yontrack"))
    }

    @Test
    fun `slug lowercases and collapses punctuation`() {
        assertEquals("my-work-tree", ItStack.slug("/home/dev/My_Work.Tree"))
    }

    @Test
    fun `slug trims leading and trailing separators`() {
        assertEquals("5-0", ItStack.slug("/home/dev/--5.0--"))
    }

    @Test
    fun `slug falls back when nothing usable is left`() {
        assertEquals("instance", ItStack.slug("/home/dev/___"))
    }

    @Test
    fun `slot is stable for a given path`() {
        val path = "/home/dev/worktrees/feature-a"
        assertEquals(ItStack.slotFromPath(path), ItStack.slotFromPath(path))
    }

    @Test
    fun `slot is always within the worktree range`() {
        repeat(200) { i ->
            val slot = ItStack.slotFromPath("/home/dev/worktrees/branch-$i")
            assertTrue(slot in ItStack.SLOT_MIN..ItStack.SLOT_MAX, "slot $slot out of range")
        }
    }

    @Test
    fun `paths sharing a basename get different slots`() {
        // The hash is over the absolute path, not the directory name.
        val a = ItStack.slotFromPath("/home/dev/one/5.0")
        val b = ItStack.slotFromPath("/home/dev/two/5.0")
        assertNotEquals(a, b)
    }

    @Test
    fun `next slot wraps without ever returning zero`() {
        assertEquals(2, ItStack.nextSlot(1))
        assertEquals(ItStack.SLOT_MIN, ItStack.nextSlot(ItStack.SLOT_MAX))
        assertEquals(ItStack.SLOT_MIN, ItStack.nextSlot(0))
    }

    @Test
    fun `ports are offset by a hundred per slot`() {
        assertEquals(5432, ItStack.port(ItStack.BASE_POSTGRES, 0))
        assertEquals(5532, ItStack.port(ItStack.BASE_POSTGRES, 1))
        assertEquals(6332, ItStack.port(ItStack.BASE_POSTGRES, 9))
    }

    @Test
    fun `no two slots ever want the same port`() {
        // Otherwise a checkout on slot 1 would be told a port is busy because
        // the checkout on slot 0 publishes it as a different service, and the
        // ten slots would degrade to fewer than ten usable instances.
        val seen = mutableMapOf<Int, Int>()
        (0..ItStack.SLOT_MAX).forEach { slot ->
            ItStack.ports(slot).forEach { port ->
                val other = seen.put(port, slot)
                assertNull(other, "port $port is wanted by both slot $other and slot $slot")
            }
        }
    }

    @Test
    fun `main checkout takes slot zero when the ports are free`() {
        val slot = ItStack.resolveSlot(
            path = "/home/dev/yontrack",
            mainCheckout = true,
            recordedSlot = null,
            portFree = { true },
        )
        assertEquals(0, slot)
    }

    @Test
    fun `a worktree never takes slot zero`() {
        val slot = ItStack.resolveSlot(
            path = "/home/dev/worktrees/feature-a",
            mainCheckout = false,
            recordedSlot = null,
            portFree = { true },
        )
        assertTrue(slot in ItStack.SLOT_MIN..ItStack.SLOT_MAX)
    }

    @Test
    fun `a busy slot is bumped along`() {
        // Slot 0 is fully taken -- a development stack in the same checkout,
        // typically -- so the main working copy moves to slot 1.
        val busy = ItStack.ports(0).toSet()
        val slot = ItStack.resolveSlot(
            path = "/home/dev/yontrack",
            mainCheckout = true,
            recordedSlot = null,
            portFree = { it !in busy },
        )
        assertEquals(1, slot)
    }

    @Test
    fun `a single busy port is enough to move the slot`() {
        val slot = ItStack.resolveSlot(
            path = "/home/dev/yontrack",
            mainCheckout = true,
            recordedSlot = null,
            portFree = { it != ItStack.BASE_VAULT },
        )
        assertEquals(1, slot)
    }

    @Test
    fun `a recorded slot is reused without probing the ports`() {
        val slot = ItStack.resolveSlot(
            path = "/home/dev/yontrack",
            mainCheckout = true,
            recordedSlot = 4,
            portFree = { error("must not probe a recorded slot") },
        )
        assertEquals(4, slot)
    }

    @Test
    fun `no free slot at all is an error`() {
        val failure = runCatching {
            ItStack.resolveSlot(
                path = "/home/dev/yontrack",
                mainCheckout = true,
                recordedSlot = null,
                portFree = { false },
            )
        }.exceptionOrNull()
        assertTrue(failure?.message?.contains("No free slot") == true, "got $failure")
    }

    @Test
    fun `every base port is probed`() {
        val probed = mutableListOf<Int>()
        ItStack.resolveSlot(
            path = "/home/dev/yontrack",
            mainCheckout = true,
            recordedSlot = null,
            portFree = { probed += it; true },
        )
        assertEquals(ItStack.ports(0), probed)
    }

    @Test
    fun `instance derives every port from its slot`() {
        val instance = ItStackInstance(slug = "feature-a", slot = 3)
        assertEquals("yontrack-it-feature-a", instance.projectName)
        assertEquals(5732, instance.postgresPort)
        assertEquals(9500, instance.elasticPort)
        assertEquals(5972, instance.rabbitPort)
        assertEquals(15972, instance.rabbitManagementPort)
        assertEquals(8500, instance.vaultPort)
        assertEquals("jdbc:postgresql://localhost:5732/ontrack", instance.jdbcUrl)
        assertEquals("http://localhost:9500", instance.elasticUri)
        assertEquals("http://localhost:8500", instance.vaultUri)
    }

    @Test
    fun `slot zero reproduces the historical ports`() {
        val instance = ItStackInstance(slug = "yontrack", slot = 0)
        assertEquals(5432, instance.postgresPort)
        assertEquals(9200, instance.elasticPort)
        assertEquals(5672, instance.rabbitPort)
        assertEquals(8200, instance.vaultPort)
        assertEquals("jdbc:postgresql://localhost:5432/ontrack", instance.jdbcUrl)
    }

    @Test
    fun `the recorded instance can be read back`() {
        val dir = createTempDir()
        try {
            val file = File(dir, "instance.env")
            assertNull(ItStack.readRecordedSlot(file))
            ItStackInstance(slug = "feature-a", slot = 6).writeInstanceEnv(file)
            assertEquals(6, ItStack.readRecordedSlot(file))
            val text = file.readText()
            assertTrue(text.contains("YONTRACK_IT_POSTGRES_PORT=6032"), text)
            assertTrue(text.contains("IT_PROJECT=yontrack-it-feature-a"), text)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createTempDir(): File =
        java.nio.file.Files.createTempDirectory("it-stack-test").toFile()
}
