package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.model.structure.NameDescription
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Yontrack starts and searches with no Elasticsearch reachable: `spring.elasticsearch.uris`
 * points at a port where nothing listens, and the export of the metrics, the only user of
 * Elasticsearch, is disabled as by default.
 */
@TestPropertySource(
    properties = [
        "spring.elasticsearch.uris=http://localhost:1",
    ]
)
class SearchWithoutElasticsearchIT : AbstractSearchTestSupport() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `No Elasticsearch client in the context`() {
        listOf(
            "co.elastic.clients.transport.rest5_client.low_level.Rest5Client",
            "co.elastic.clients.elasticsearch.ElasticsearchClient",
            "org.springframework.boot.elasticsearch.health.ElasticsearchRestClientHealthIndicator",
        ).forEach { className ->
            val type = Class.forName(className)
            assertTrue(
                applicationContext.getBeanNamesForType(type).isEmpty(),
                "No bean of type $className"
            )
        }
    }

    @Test
    fun `Searching with no Elasticsearch`() {
        val name = "p" + UUID.randomUUID().toString().replace("-", "").take(15)
        project(NameDescription.nd(name, ""))
        val data = run(
            """{
                search(query: "$name") {
                    total
                    items { title type { id } }
                }
            }"""
        )
        val search = data["search"]
        assertEquals(1, search["total"].asInt())
        assertEquals(name, search["items"][0]["title"].asText())
        assertEquals("project", search["items"][0]["type"]["id"].asText())
    }

}
