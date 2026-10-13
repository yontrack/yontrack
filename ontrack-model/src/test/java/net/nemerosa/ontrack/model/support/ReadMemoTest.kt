package net.nemerosa.ontrack.model.support

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReadMemoTest {

    private val names = ReadMemo.Region<Int, String>("names")
    private val others = ReadMemo.Region<Int, String>("others")
    private val optional = ReadMemo.Region<Int, String?>("optional")

    /**
     * Loader counting its calls
     */
    private class Loader<V>(private val value: () -> V) : () -> V {
        var calls = 0
        override fun invoke(): V {
            calls++
            return value()
        }
    }

    @Test
    fun `With no unit of work, every read loads`() {
        val loader = Loader { "one" }
        assertNull(ReadMemo.current(), "No memo open")
        assertEquals("one", ReadMemo.read(names, 1, loader))
        assertEquals("one", ReadMemo.read(names, 1, loader))
        assertEquals(2, loader.calls)
    }

    @Test
    fun `In a unit of work, a key is loaded once`() {
        val loader = Loader { "one" }
        ReadMemo.unitOfWork {
            assertEquals("one", ReadMemo.read(names, 1, loader))
            assertEquals("one", ReadMemo.read(names, 1, loader))
        }
        assertEquals(1, loader.calls)
    }

    @Test
    fun `In a unit of work, a null value is memorised too`() {
        val loader = Loader<String?> { null }
        ReadMemo.unitOfWork {
            assertNull(ReadMemo.read(optional, 1, loader))
            assertNull(ReadMemo.read(optional, 1, loader))
        }
        assertEquals(1, loader.calls)
    }

    @Test
    fun `Keys and regions are distinct`() {
        ReadMemo.unitOfWork {
            assertEquals("one", ReadMemo.read(names, 1) { "one" })
            assertEquals("two", ReadMemo.read(names, 2) { "two" })
            assertEquals("other", ReadMemo.read(others, 1) { "other" })
            assertEquals("one", ReadMemo.read(names, 1) { "not loaded" })
        }
    }

    @Test
    fun `Each unit of work starts empty`() {
        val loader = Loader { "one" }
        ReadMemo.unitOfWork { ReadMemo.read(names, 1, loader) }
        ReadMemo.unitOfWork { ReadMemo.read(names, 1, loader) }
        assertEquals(2, loader.calls)
    }

    @Test
    fun `A written value replaces the memorised one`() {
        ReadMemo.unitOfWork {
            ReadMemo.read(names, 1) { "one" }
            ReadMemo.written(names, 1, "uno")
            assertEquals("uno", ReadMemo.read(names, 1) { "not loaded" })
        }
    }

    @Test
    fun `An evicted value is loaded again`() {
        val loader = Loader { "one" }
        ReadMemo.unitOfWork {
            ReadMemo.read(names, 1, loader)
            ReadMemo.evict(names, 1)
            ReadMemo.read(names, 1, loader)
        }
        assertEquals(2, loader.calls)
    }

    @Test
    fun `Writing and evicting with no unit of work do nothing`() {
        ReadMemo.written(names, 1, "uno")
        ReadMemo.evict(names, 1)
        assertNull(ReadMemo.current())
    }

    @Test
    fun `The memo tells whether it holds a key`() {
        ReadMemo.unitOfWork {
            val memo = assertNotNull(ReadMemo.current())
            assertFalse(memo.contains(optional, 1))
            memo.put(optional, 1, null)
            assertTrue(memo.contains(optional, 1), "A null value is held")
        }
    }

    @Test
    fun `A nested unit of work joins the current one`() {
        val loader = Loader { "one" }
        ReadMemo.unitOfWork {
            val outer = ReadMemo.current()
            ReadMemo.read(names, 1, loader)
            ReadMemo.unitOfWork {
                assertSame(outer, ReadMemo.current(), "Same memo")
                ReadMemo.read(names, 1, loader)
            }
            assertSame(outer, ReadMemo.current(), "Still open after the nested unit")
        }
        assertEquals(1, loader.calls)
        assertNull(ReadMemo.current(), "Closed by the unit which opened it")
    }

    @Test
    fun `A unit of work returns the value of its code`() {
        assertEquals(42, ReadMemo.unitOfWork { 42 })
    }

    @Test
    fun `The memo is closed when the code of the unit fails`() {
        assertThrows<IllegalStateException> {
            ReadMemo.unitOfWork { error("Failure") }
        }
        assertNull(ReadMemo.current())
    }

    @Test
    fun `Another thread finds no memo`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ReadMemo.unitOfWork {
                val loader = Loader { "one" }
                ReadMemo.read(names, 1, loader)
                val other = executor.submit<Boolean> { ReadMemo.current() == null }.get(10, TimeUnit.SECONDS)
                assertTrue(other, "No memo on another thread")
                executor.submit { ReadMemo.read(names, 1, loader) }.get(10, TimeUnit.SECONDS)
                assertEquals(2, loader.calls, "Loaded again by the other thread")
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `A loader may read the memo itself`() {
        ReadMemo.unitOfWork {
            val value = ReadMemo.read(names, 1) {
                ReadMemo.read(others, 1) { "inner" } + "-outer"
            }
            assertEquals("inner-outer", value)
            assertEquals("inner", ReadMemo.read(others, 1) { "not loaded" })
        }
    }
}
