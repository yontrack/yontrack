package net.nemerosa.ontrack.service.elasticsearch

import co.elastic.clients.json.JsonpMapper
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.io.StringWriter
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * The search documents are written by the `JsonpMapper` of the Elasticsearch client, and their
 * fields hold Jackson 2 types. Spring Boot 4 would give the client a Jackson 3 mapper, which writes
 * a Jackson 2 `JsonNode` as a bean.
 */
class ElasticSearchJsonpMapperIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var jsonpMapper: JsonpMapper

    @Test
    fun `search documents are written with Jackson 2`() {
        val document = mapOf(
            "node" to mapOf("name" to "test", "count" to 2).asJson(),
            "time" to LocalDateTime.of(2026, 9, 21, 10, 30),
        )

        val output = StringWriter()
        jsonpMapper.jsonProvider().createGenerator(output).use { generator ->
            jsonpMapper.serialize(document, generator)
        }

        assertEquals(
            """{"node":{"name":"test","count":2},"time":"2026-09-21T10:30:00"}""",
            output.toString()
        )
    }

}
