package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StorageServiceIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var storageService: StorageService

    @Test
    fun store_type_none() {
        val retrieved = storageService.find(
            TestUtils.uid("C"), "1",
            StoredValue::class
        )
        assertNull(retrieved)
    }

    @Test
    fun store_type() {
        val stored = StoredValue("test")
        val store = TestUtils.uid("C")
        storageService.store(store, "1", stored)
        val retrieved = storageService.find(
            store, "1",
            StoredValue::class
        )
        assertNotNull(retrieved)
        assertEquals(stored, retrieved)
    }

    @Test
    fun store_Data() {
        val stored1 = StoredValue("test-1")
        val stored2 = StoredValue("test-2")
        val store = TestUtils.uid("C")

        storageService.store(store, "1", stored1)
        storageService.store(store, "2", stored2)

        val data = storageService.getData(
            store,
            StoredValue::class.java
        )

        assertEquals(2, data.size.toLong())
        assertEquals(stored1, data["1"])
        assertEquals(stored2, data["2"])
    }

    @Test
    fun `A counter starts at 1 and is incremented`() {
        val store = TestUtils.uid("C")
        assertEquals(0, storageService.getCount(store, "1"), "No counter yet")
        assertEquals(1, storageService.increment(store, "1"))
        assertEquals(2, storageService.increment(store, "1"))
        assertEquals(1, storageService.increment(store, "2"), "Counters are per key")
        assertEquals(2, storageService.getCount(store, "1"))
    }

    @Test
    fun `A counter is reset by deleting it`() {
        val store = TestUtils.uid("C")
        storageService.increment(store, "1")
        storageService.increment(store, "1")
        storageService.delete(store, "1")
        assertEquals(0, storageService.getCount(store, "1"))
        assertEquals(1, storageService.increment(store, "1"))
    }

    /**
     * Each increment commits on its own connection, as on several replicas.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Concurrent increments are all counted`() {
        val store = TestUtils.uid("C")
        val executor = Executors.newFixedThreadPool(8)
        try {
            val counts = executor.invokeAll(
                (1..40).map { Callable { storageService.increment(store, "1") } }
            ).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals((1..40).toList(), counts.sorted(), "Each increment gives its own count")
            assertEquals(40, storageService.getCount(store, "1"))
        } finally {
            executor.shutdownNow()
            storageService.clear(store)
        }
    }
}
