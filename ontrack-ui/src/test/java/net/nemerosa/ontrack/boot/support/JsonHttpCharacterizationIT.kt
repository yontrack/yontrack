package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.extension.general.MessageProperty
import net.nemerosa.ontrack.extension.general.MessagePropertyType
import net.nemerosa.ontrack.extension.general.MessageType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.model.structure.User
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * The JSON a client reads over HTTP, pinned before the Jackson 3 migration (#1843), from a REST
 * endpoint and from GraphQL. Written on Jackson 2, and meant to pass unchanged on Jackson 3 — except
 * where the migration deliberately gives the MVC converters the `ObjectMapperFactory` configuration,
 * which is recorded on the migration page.
 *
 * Not transactional: the token the calls authenticate with must be committed for the server, on its
 * own threads, to find it.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JsonHttpCharacterizationIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newHttpClient()

    private val token: String by lazy {
        asAdmin().call {
            tokensService.generateNewToken(TokenOptions(name = uid("json-"))).value
        }
    }

    private val creation = LocalDateTime.of(2025, 11, 4, 9, 12, 30, 123_400_000)

    private fun datedBuild(): Build = asAdmin().call {
        val branch = project().branch()
        structureService.newBuild(
            Build.of(
                branch,
                NameDescription.nd(uid("B"), "Build"),
                Signature(creation, User("admin")),
            )
        ).apply {
            setProperty(this, MessagePropertyType::class.java, MessageProperty(MessageType.WARNING, "Some message"))
        }
    }

    private fun get(path: String): String {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
        return response.body()
    }

    private fun graphQL(query: String): String {
        val body = mapOf("query" to query)
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/graphql"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.asJson().asJsonString()))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
        return response.body()
    }

    @Test
    fun `REST payload of a build`() {
        val build = datedBuild()
        val body = get("/rest/structure/entity/build/${build.project.name}/${build.branch.name}/${build.name}")
        val json = body.parseAsJson()
        assertEquals(build.id(), json.path("id").asInt())
        assertEquals(build.name, json.path("name").asText())
        assertEquals(
            """{"time":[2025,11,4,9,12,30,123400000],"user":{"name":"admin"}}""",
            json.path("signature").toString(),
            "Signature of the build as written by the MVC converters: $body"
        )
    }

    @Test
    fun `GraphQL output of the JSON scalar and of the dates`() {
        val build = datedBuild()
        val body = graphQL(
            """{
                builds(id: ${build.id}) {
                    creation { user time }
                    properties(type: "net.nemerosa.ontrack.extension.general.MessagePropertyType") { value }
                }
            }"""
        )
        assertEquals(
            """{"data":{"builds":[{"creation":{"user":"admin","time":"2025-11-04T09:12:30.1234"},"properties":[{"value":{"type":"WARNING","text":"Some message"}}]}]}}""",
            body,
        )
    }

}
