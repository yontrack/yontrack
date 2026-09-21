package net.nemerosa.ontrack.extension.support.client

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.getForObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Jackson2RestTemplatesTest {

    data class Sample(val name: String, val count: Int)

    @Test
    fun `the JSON converter is the Jackson 2 one`() {
        val converters = jackson2RestTemplateBuilder().build().messageConverters
        assertTrue(converters.any { it is MappingJackson2HttpMessageConverter })
        assertTrue(
            converters.none { it.javaClass.name == "org.springframework.http.converter.json.JacksonJsonHttpMessageConverter" },
            "The Jackson 3 converter is not registered"
        )
    }

    @Test
    fun `reading a Jackson 2 JSON node`() {
        val template = jackson2RestTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(template).build()
        server.expect(requestTo("/sample"))
            .andRespond(withSuccess("""{"name":"test","count":2}""", MediaType.APPLICATION_JSON))

        val node = template.getForObject<JsonNode>("/sample")

        assertEquals("test", node?.path("name")?.asText())
        assertEquals(2, node?.path("count")?.asInt())
        server.verify()
    }

    @Test
    fun `reading a Kotlin data class`() {
        val template = jackson2RestTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(template).build()
        server.expect(requestTo("/sample"))
            .andRespond(withSuccess("""{"name":"test","count":2,"unknown":true}""", MediaType.APPLICATION_JSON))

        val sample = template.getForObject<Sample>("/sample")

        assertEquals(Sample("test", 2), sample)
        server.verify()
    }

}
