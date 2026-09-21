package net.nemerosa.ontrack.extension.gitlab.casc

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.extension.gitlab.gitLabTestConfigMock
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitLabConfigurationCascContextIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var gitLabConfigurationService: GitLabConfigurationService

    @Autowired
    private lateinit var gitLabConfigurationCascContext: GitLabConfigurationCascContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @BeforeEach
    fun init() {
        asAdmin {
            gitLabConfigurationService.configurations.forEach { config ->
                gitLabConfigurationService.deleteConfiguration(config.name)
            }
        }
    }

    @Test
    fun `CasC schema type`() {
        val type = gitLabConfigurationCascContext.jsonType(jsonTypeBuilder)
        val items = type.asJson().path("items")
        assertEquals("GitLabConfigurationCascData", items.path("title").asText())
        assertEquals(
            setOf("name", "url", "token", "ignoreSslCertificate"),
            items.path("properties").propertyNames().asSequence().toSet()
        )
        assertEquals(
            setOf("name", "url", "token"),
            items.path("required").values().map { it.asText() }.toSet()
        )
        assertEquals(false, items.path("additionalProperties").asBoolean(true))
    }

    @Test
    fun `GitLab configuration CasC`() {
        val name = uid("C")
        withDisabledConfigurationTest {
            casc(
                """
                    ontrack:
                        config:
                            gitlab:
                                - name: $name
                                  url: https://gitlab.com
                                  token: secret
                """.trimIndent()
            )
            asAdmin {
                assertEquals(
                    GitLabConfiguration(
                        name = name,
                        url = "https://gitlab.com",
                        token = "secret",
                    ),
                    gitLabConfigurationService.getConfiguration(name)
                )
            }
        }
    }

    @Test
    fun `GitLab configuration CasC with the SSL certificate ignored`() {
        val name = uid("C")
        withDisabledConfigurationTest {
            casc(
                """
                    ontrack:
                        config:
                            gitlab:
                                - name: $name
                                  url: https://gitlab.example.com
                                  token: secret
                                  ignoreSslCertificate: true
                """.trimIndent()
            )
            asAdmin {
                assertEquals(
                    GitLabConfiguration(
                        name = name,
                        url = "https://gitlab.example.com",
                        token = "secret",
                        ignoreSslCertificate = true,
                    ),
                    gitLabConfigurationService.getConfiguration(name)
                )
            }
        }
    }

    @Test
    fun `GitLab configuration CasC - updating a configuration`() {
        withDisabledConfigurationTest {
            val config = gitLabTestConfigMock()
            asAdmin {
                gitLabConfigurationService.newConfiguration(config)
            }
            casc(
                """
                    ontrack:
                        config:
                            gitlab:
                                - name: ${config.name}
                                  url: https://gitlab.other.com
                                  token: new-token
                """.trimIndent()
            )
            asAdmin {
                val saved = gitLabConfigurationService.getConfiguration(config.name)
                assertEquals("https://gitlab.other.com", saved.url)
                assertEquals("new-token", saved.token)
            }
        }
    }

    @Test
    fun `GitLab configuration CasC - removing and adding a configuration`() {
        withDisabledConfigurationTest {
            val config1 = gitLabTestConfigMock()
            asAdmin {
                gitLabConfigurationService.newConfiguration(config1)
            }
            val config2 = gitLabTestConfigMock()
            casc(
                """
                    ontrack:
                        config:
                            gitlab:
                                - name: ${config2.name}
                                  url: ${config2.url}
                                  token: ${config2.token}
                """.trimIndent()
            )
            asAdmin {
                assertNull(gitLabConfigurationService.findConfiguration(config1.name), "Old config has been removed")
                assertEquals(config2, gitLabConfigurationService.getConfiguration(config2.name))
            }
        }
    }

    @Test
    fun `Rendering does not expose the token`() {
        withDisabledConfigurationTest {
            val config = gitLabTestConfigMock()
            asAdmin {
                gitLabConfigurationService.newConfiguration(config)
                assertEquals(
                    """
                        [{
                            "name": "${config.name}",
                            "url": "${config.url}",
                            "token": "",
                            "ignoreSslCertificate": false
                        }]
                    """.trimIndent().parseAsJson(),
                    gitLabConfigurationCascContext.render()
                )
            }
        }
    }

}
