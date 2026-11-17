package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.extension.casc.CascConfigurationProperties
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals

@TestPropertySource(
    properties = [
        "ontrack.config.casc.secrets.type=file",
    ]
)
class HttpConfigurationCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var httpConfigurationService: HttpConfigurationService

    @Autowired
    private lateinit var httpConfigurationCasc: HttpConfigurationCasc

    @Autowired
    private lateinit var cascConfigurationProperties: CascConfigurationProperties

    @BeforeEach
    fun secrets() {
        val dir = createTempDirectory()
        dir.resolve("my-api").createDirectories().resolve("token").writeText("secret-token")
        cascConfigurationProperties.secrets.directory = dir.absolutePathString()
    }

    @Test
    fun `Defining HTTP configurations with a secret`() {
        val name = uid("http-")
        val anonymous = uid("http-")
        asAdmin {
            withDisabledConfigurationTest {
                casc(
                    """
                        ontrack:
                            config:
                                http:
                                    - name: $name
                                      url: https://api.example.com/v1
                                      authentication:
                                        type: bearer
                                        config:
                                            token: {{ secret.my-api.token }}
                                    - name: $anonymous
                                      url: https://public.example.com
                    """.trimIndent()
                )
            }
            assertEquals(
                listOf(
                    HttpConfiguration(
                        name = name,
                        url = "https://api.example.com/v1",
                        authentication = HttpAuthentication(
                            type = "bearer",
                            config = mapOf("token" to "secret-token").asJson(),
                        ),
                    ),
                    HttpConfiguration(
                        name = anonymous,
                        url = "https://public.example.com",
                    ),
                ).sortedBy { it.name },
                httpConfigurationService.configurations.sortedBy { it.name },
            )
        }
    }

    @Test
    fun `Updating and deleting HTTP configurations`() {
        val name = uid("http-")
        val other = uid("http-")
        asAdmin {
            withDisabledConfigurationTest {
                casc(
                    """
                        ontrack:
                            config:
                                http:
                                    - name: $name
                                      url: https://api.example.com/v1
                                    - name: $other
                                      url: https://other.example.com
                    """.trimIndent()
                )
                casc(
                    """
                        ontrack:
                            config:
                                http:
                                    - name: $name
                                      url: https://api.example.com/v2
                                      authentication:
                                        type: basic
                                        config:
                                            username: user
                                            password: {{ secret.my-api.token }}
                    """.trimIndent()
                )
            }
            assertEquals(
                listOf(
                    HttpConfiguration(
                        name = name,
                        url = "https://api.example.com/v2",
                        authentication = HttpAuthentication(
                            type = "basic",
                            config = mapOf("username" to "user", "password" to "secret-token").asJson(),
                        ),
                    ),
                ),
                httpConfigurationService.configurations,
            )
        }
    }

    @Test
    fun `Rendering the HTTP configurations obfuscates the secrets`() {
        val name = uid("http-")
        asAdmin {
            httpConfigurationService.configurations.forEach {
                httpConfigurationService.deleteConfiguration(it.name)
            }
            withDisabledConfigurationTest {
                httpConfigurationService.newConfiguration(
                    HttpConfiguration(
                        name = name,
                        url = "https://api.example.com",
                        authentication = HttpAuthentication(
                            type = "header",
                            config = mapOf("name" to "X-Api-Key", "value" to "secret").asJson(),
                        ),
                    )
                )
            }
            assertEquals(
                listOf(
                    mapOf(
                        "name" to name,
                        "url" to "https://api.example.com",
                        "authentication" to mapOf(
                            "type" to "header",
                            "config" to mapOf(
                                "name" to "X-Api-Key",
                                "value" to "",
                            ),
                        ),
                    )
                ).asJson(),
                httpConfigurationCasc.render(),
            )
        }
    }

}
