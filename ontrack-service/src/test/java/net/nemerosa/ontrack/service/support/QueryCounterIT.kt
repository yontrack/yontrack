package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The [QueryCounter] records the queries run against the database of the tests.
 */
class QueryCounterIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var queryCounter: QueryCounter

    private fun readSetting(category: String) {
        namedParameterJdbcTemplate.queryForList(
            "SELECT VALUE FROM SETTINGS WHERE CATEGORY = :category",
            mapOf("category" to category),
            String::class.java,
        )
    }

    @Test
    fun `The queries run by the code are recorded with their parameters`() {
        val category = uid("c-")
        val queries = queryCounter.record {
            readSetting(category)
            readSetting(category)
        }
        val settings = queries.on("SETTINGS").withParameter(category)
        assertEquals(2, settings.size, "Two queries: $queries")
        assertEquals(listOf<Any?>(category), settings.first().parameters)
        assertTrue(settings.first().sql.contains("SELECT VALUE FROM SETTINGS"), settings.first().sql)
    }

    @Test
    fun `The queries run outside the recording are not recorded`() {
        val category = uid("c-")
        readSetting(category)
        val queries = queryCounter.record { }
        readSetting(category)
        assertEquals(0, queries.withParameter(category).size)
    }

    @Test
    fun `The queries of the other threads are recorded with their thread`() {
        val category = uid("c-")
        val executor = Executors.newSingleThreadExecutor { Thread(it, "query-counter-test") }
        try {
            val queries = queryCounter.record {
                executor.submit { readSetting(category) }.get(30, TimeUnit.SECONDS)
            }
            val recorded = queries.withParameter(category)
            assertEquals(1, recorded.size)
            assertEquals("query-counter-test", recorded.first().thread)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `Queries are selected by table`() {
        val category = uid("c-")
        val queries = queryCounter.record {
            readSetting(category)
            namedParameterJdbcTemplate.queryForList("SELECT ID FROM PROJECTS WHERE NAME = :name", mapOf("name" to category))
        }
        assertEquals(1, queries.on("settings").withParameter(category).size, "Table name is not case sensitive")
        assertEquals(1, queries.on("PROJECTS").withParameter(category).size)
        assertEquals(0, queries.on("SETTING").withParameter(category).size, "Whole table name")
    }
}
