package net.nemerosa.ontrack.extension.support.client

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestTemplatesTest {

    data class Sample(val name: String, val count: Int)

    data class Ordered(val zeta: Int, val alpha: Int)

    @Test
    fun `the JSON converter is the Jackson 3 one`() {
        val converters = restTemplateBuilder().build().messageConverters
        assertTrue(converters.any { it is JacksonJsonHttpMessageConverter })
    }

    @Test
    fun `reading a JSON node`() {
        val template = restTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(template).build()
        server.expect(requestTo("/sample"))
            .andRespond(withSuccess("""{"name":"test","count":2}""", MediaType.APPLICATION_JSON))

        val node = template.getForObject<JsonNode>("/sample")

        assertEquals("test", node?.path("name")?.asString())
        assertEquals(2, node?.path("count")?.asInt())
        server.verify()
    }

    @Test
    fun `reading a Kotlin data class ignores the unknown properties`() {
        val template = restTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(template).build()
        server.expect(requestTo("/sample"))
            .andRespond(withSuccess("""{"name":"test","count":2,"unknown":true}""", MediaType.APPLICATION_JSON))

        val sample = template.getForObject<Sample>("/sample")

        assertEquals(Sample("test", 2), sample)
        server.verify()
    }

    @Test
    fun `writing keeps the declaration order of the properties`() {
        val template = restTemplateBuilder().build()
        val server = MockRestServiceServer.bindTo(template).build()
        server.expect(requestTo("/sample"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string("""{"zeta":1,"alpha":2}"""))
            .andRespond(withSuccess("""{"name":"test","count":2}""", MediaType.APPLICATION_JSON))

        template.postForObject<Sample>("/sample", Ordered(zeta = 1, alpha = 2))

        server.verify()
    }

}
