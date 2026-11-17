package net.nemerosa.ontrack.extension.notifications.http.config

import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class HttpConfigurationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var httpConfigurationService: HttpConfigurationService

    private fun basic(password: String = "secret-password") = HttpAuthentication(
        type = "basic",
        config = mapOf("username" to "user", "password" to password).asJson(),
    )

    private fun createConfiguration(
        name: String = uid("http-"),
        authentication: HttpAuthentication = basic(),
    ): HttpConfiguration = asAdmin {
        withDisabledConfigurationTest {
            HttpConfiguration(
                name = name,
                url = "https://api.example.com/v1",
                authentication = authentication,
            ).apply {
                httpConfigurationService.newConfiguration(this)
            }
        }
    }

    @Test
    fun `The authentication configuration is stored encrypted and read back in clear`() {
        val config = createConfiguration()
        val raw = namedParameterJdbcTemplate!!.queryForObject(
            "SELECT CONTENT::TEXT FROM CONFIGURATIONS WHERE TYPE = :type AND NAME = :name",
            mapOf("type" to HttpConfiguration::class.java.name, "name" to config.name),
            String::class.java,
        )!!
        assertFalse(raw.contains("secret-password"), "The secret is not stored in clear: $raw")
        val stored = raw.parseAsJson()
        assertEquals("basic", stored.path("authentication").path("type").asString())
        assertEquals("https://api.example.com/v1", stored.path("url").asString())
        asAdmin {
            assertEquals(config, httpConfigurationService.getConfiguration(config.name))
        }
    }

    @Test
    fun `Updating with a blank secret keeps the existing one`() {
        val config = createConfiguration()
        asAdmin {
            withDisabledConfigurationTest {
                httpConfigurationService.updateConfiguration(
                    config.name,
                    config.copy(url = "https://api.example.com/v2", authentication = basic(password = "")),
                )
            }
            assertEquals(
                config.copy(url = "https://api.example.com/v2"),
                httpConfigurationService.getConfiguration(config.name),
            )
        }
    }

    @Test
    fun `Listing the configurations obfuscates the authentication`() {
        val config = createConfiguration()
        asAdmin {
            run(
                """
                    {
                        configurations(configurationType: "http") {
                            name
                            data
                        }
                    }
                """
            ) { data ->
                val node = data.path("configurations").single { it.path("name").asString() == config.name }
                    .path("data")
                assertEquals("https://api.example.com/v1", node.path("url").asString())
                val authentication = node.path("authentication")
                assertEquals("basic", authentication.path("type").asString())
                assertEquals("user", authentication.path("config").path("username").asString())
                // Blank, or left out of the JSON altogether
                assertEquals(
                    "",
                    authentication.path("config").path("password").asString(""),
                    "The password is not exposed",
                )
            }
        }
    }

    @Test
    fun `Creating and updating a configuration with the generic mutations`() {
        val name = uid("http-")
        asAdmin {
            withDisabledConfigurationTest {
                run(
                    """
                        mutation {
                            createConfiguration(input: {
                                type: "http",
                                name: "$name",
                                data: {
                                    url: "https://api.example.com",
                                    authentication: {
                                        type: "header",
                                        config: {
                                            name: "X-Api-Key",
                                            value: "my-key"
                                        }
                                    }
                                }
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "createConfiguration")
                }
                run(
                    """
                        mutation {
                            updateConfiguration(input: {
                                type: "http",
                                name: "$name",
                                data: {
                                    url: "https://api.example.com/v2",
                                    authentication: {
                                        type: "header",
                                        config: {
                                            name: "X-Api-Key",
                                            value: ""
                                        }
                                    }
                                }
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "updateConfiguration")
                }
            }
            assertNotNull(httpConfigurationService.findConfiguration(name)) {
                assertEquals("https://api.example.com/v2", it.url)
                assertEquals(
                    HttpAuthentication(
                        type = "header",
                        config = mapOf("name" to "X-Api-Key", "value" to "my-key").asJson(),
                    ),
                    it.authentication,
                )
            }
        }
    }

    @Test
    fun `Creating a configuration with an invalid URL`() {
        asAdmin {
            withDisabledConfigurationTest {
                run(
                    """
                        mutation {
                            createConfiguration(input: {
                                type: "http",
                                name: "${uid("http-")}",
                                data: {
                                    url: "https://user:password@api.example.com"
                                }
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    assertUserError(
                        data, "createConfiguration",
                        message = """Base URL "https://user:password@api.example.com" is not valid: credentials go in the authentication, not in the URL."""
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a configuration with an unknown authentication type`() {
        asAdmin {
            withDisabledConfigurationTest {
                run(
                    """
                        mutation {
                            createConfiguration(input: {
                                type: "http",
                                name: "${uid("http-")}",
                                data: {
                                    url: "https://api.example.com",
                                    authentication: { type: "oauth2" }
                                }
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    assertUserError(
                        data, "createConfiguration",
                        message = """Authentication type "oauth2" is not supported."""
                    )
                }
            }
        }
    }

    @Test
    fun `Testing a configuration refused by the API`() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                val status = if (exchange.requestHeaders.getFirst("Authorization") == "Bearer good") 404 else 401
                exchange.sendResponseHeaders(status, -1L)
                exchange.close()
            }
            start()
        }
        try {
            val url = "http://${server.address.hostString}:${server.address.port}/api"
            asAdmin {
                listOf("good" to "OK", "bad" to "ERROR").forEach { (token, expected) ->
                    run(
                        """
                            mutation {
                                testConfiguration(input: {
                                    type: "http",
                                    name: "${uid("http-")}",
                                    data: {
                                        url: "$url",
                                        authentication: { type: "bearer", config: { token: "$token" } }
                                    }
                                }) {
                                    connectionResult { type message }
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        val result = checkGraphQLUserErrors(data, "testConfiguration").path("connectionResult")
                        assertEquals(expected, result.path("type").asString(), result.path("message").asString())
                    }
                }
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `List of the authentication types`() {
        run(
            """
                {
                    httpAuthenticators { type displayName }
                    webhookAuthenticators { type }
                }
            """
        ) { data ->
            assertEquals(
                listOf("basic", "bearer", "header", "none"),
                data.path("httpAuthenticators").toList().map { it.path("type").asString() },
            )
            assertEquals(
                "No authentication",
                data.path("httpAuthenticators").last().path("displayName").asString(),
            )
            assertEquals(
                listOf("basic", "bearer", "header"),
                data.path("webhookAuthenticators").toList().map { it.path("type").asString() },
                "Webhooks keep their authentication types",
            )
        }
    }

}
