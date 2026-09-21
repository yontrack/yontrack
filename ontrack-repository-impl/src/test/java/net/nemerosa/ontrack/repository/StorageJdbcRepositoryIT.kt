package net.nemerosa.ontrack.repository

import tools.jackson.core.JacksonException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getIntField
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Transactional
class StorageJdbcRepositoryIT : AbstractRepositoryTestSupport() {

    @Autowired
    private lateinit var repository: StorageRepository

    @Test
    fun no_data_returns_empty() {
        val o = repository.retrieveJson(uid("C"), "1")
        assertNull(o)
    }

    @Test
    fun store_and_retrieve() {
        val json = mapOf("name" to "My name").asJson()
        val store = uid("C")
        repository.storeJson(store, "1", json)
        val o = repository.retrieveJson(store, "1")
        assertNotNull(o) {
            assertEquals(json, it)
        }
    }

    @Test
    fun store_and_delete() {
        val json = mapOf("name" to "My name").asJson()
        val store = uid("C")
        repository.storeJson(store, "1", json)
        repository.delete(store, "1")
        val o = repository.retrieveJson(store, "1")
        assertNull(o)
    }

    @Test
    fun get_keys() {
        val store = uid("C")
        repository.storeJson(store, "1", mapOf("name" to "1").asJson())
        repository.storeJson(store, "2", mapOf("name" to "2").asJson())
        assertEquals(
            listOf("1", "2"),
            repository.getKeys(store)
        )
    }

    @Test
    @Throws(JacksonException::class)
    fun get_data() {
        val store = uid("C")
        val data1 = mapOf("name" to "1").asJson()
        val data2 = mapOf("name" to "2").asJson()
        repository.storeJson(store, "1", data1)
        repository.storeJson(store, "2", data2)
        val data = repository.getData(store)
        assertEquals(2, data.size)
        assertEquals(data1, data["1"])
        assertEquals(data2, data["2"])
    }

    @Test
    fun `Filtering with paging`() {
        val store = uid("C")
        repeat(20) {
            val node = mapOf("value" to it).asJson()
            repository.storeJson(store, it.toString(), node)
        }
        repository.filter(store, size = 10, orderQuery = "order by (data->>'value')::int asc").let { items ->
            assertEquals(10, items.size)
            assertEquals((0..9).toList(), items.map { it.getIntField("value") })
        }
        repository.filter(store, offset = 10, size = 5, orderQuery = "order by (data->>'value')::int asc")
            .let { items ->
                assertEquals(5, items.size)
                assertEquals((10..14).toList(), items.map { it.getIntField("value") })
            }
        repository.filter(store, offset = 15, size = 10, orderQuery = "order by (data->>'value')::int asc")
            .let { items ->
                assertEquals(5, items.size)
                assertEquals((15..19).toList(), items.map { it.getIntField("value") })
            }
    }

    @Test
    fun `Filtering with query`() {
        val store = uid("C")
        repeat(20) {
            val node = mapOf("value" to it).asJson()
            repository.storeJson(store, it.toString(), node)
        }
        repository.filter(
            store,
            size = 10,
            query = "(data->>'value')::int = :value",
            queryVariables = mapOf("value" to 10)
        ).let { items ->
            assertEquals(1, items.size)
            assertEquals(10, items.first().getIntField("value"))
        }
    }
}