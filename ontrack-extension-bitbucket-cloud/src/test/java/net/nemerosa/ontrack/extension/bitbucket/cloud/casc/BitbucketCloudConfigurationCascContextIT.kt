package net.nemerosa.ontrack.extension.bitbucket.cloud.casc

import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BitbucketCloudConfigurationCascContextIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var bitbucketCloudConfigurationService: BitbucketCloudConfigurationService

    @Autowired
    private lateinit var bitbucketCloudConfigurationCascContext: BitbucketCloudConfigurationCascContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = bitbucketCloudConfigurationCascContext.jsonType(jsonTypeBuilder)
        val items = type.asJson().path("items")
        assertEquals("BitbucketCloudConfigurationCascData", items.path("title").asText())
        assertEquals(
            setOf("name", "authType", "email", "token", "autoMergeEmail", "autoMergeToken"),
            items.path("properties").propertyNames().asSequence().toSet()
        )
        assertEquals(
            setOf("name", "authType", "token"),
            items.path("required").values().map { it.asText() }.toSet()
        )
        assertEquals(
            setOf("API_TOKEN", "ACCESS_TOKEN"),
            items.path("properties").path("authType").path("enum").values().map { it.asText() }.toSet()
        )
        assertEquals(false, items.path("additionalProperties").asBoolean(true))
    }

    @BeforeEach
    fun init() {
        asAdmin {
            val names = bitbucketCloudConfigurationService.configurations.map { it.name }
            names.forEach { name ->
                bitbucketCloudConfigurationService.deleteConfiguration(name)
            }
        }
    }

    @Test
    fun `Bitbucket Cloud API token configuration CasC`() {
        val name = uid("C")
        withDisabledConfigurationTest {
            casc(
                """
                    ontrack:
                        config:
                            bitbucket-cloud:
                                - name: $name
                                  authType: API_TOKEN
                                  email: bot@example.com
                                  token: secret
                                  autoMergeEmail: approver@example.com
                                  autoMergeToken: approver-secret
                """.trimIndent()
            )
            asAdmin {
                assertEquals(
                    BitbucketCloudConfiguration(
                        name = name,
                        authType = BitbucketCloudAuthType.API_TOKEN,
                        email = "bot@example.com",
                        token = "secret",
                        autoMergeEmail = "approver@example.com",
                        autoMergeToken = "approver-secret",
                    ),
                    bitbucketCloudConfigurationService.getConfiguration(name)
                )
            }
        }
    }

    @Test
    fun `Bitbucket Cloud access token configuration CasC`() {
        val name = uid("C")
        withDisabledConfigurationTest {
            casc(
                """
                    ontrack:
                        config:
                            bitbucket-cloud:
                                - name: $name
                                  authType: ACCESS_TOKEN
                                  token: secret
                """.trimIndent()
            )
            asAdmin {
                assertEquals(
                    BitbucketCloudConfiguration(
                        name = name,
                        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
                        token = "secret",
                    ),
                    bitbucketCloudConfigurationService.getConfiguration(name)
                )
            }
        }
    }

    @Test
    fun `Bitbucket Cloud configuration CasC - updating a configuration`() {
        withDisabledConfigurationTest {
            val config = bitbucketCloudTestConfigMock()
            asAdmin {
                bitbucketCloudConfigurationService.newConfiguration(config)
            }
            casc(
                """
                    ontrack:
                        config:
                            bitbucket-cloud:
                                - name: ${config.name}
                                  authType: API_TOKEN
                                  email: other@example.com
                                  token: new-token
                """.trimIndent()
            )
            asAdmin {
                val savedConfig = bitbucketCloudConfigurationService.getConfiguration(config.name)
                assertEquals("other@example.com", savedConfig.email)
                assertEquals("new-token", savedConfig.token)
            }
        }
    }

    @Test
    fun `Bitbucket Cloud configuration CasC - removing and adding a configuration`() {
        withDisabledConfigurationTest {
            val config1 = bitbucketCloudTestConfigMock()
            asAdmin {
                bitbucketCloudConfigurationService.newConfiguration(config1)
            }
            val config2 = bitbucketCloudTestConfigMock(authType = BitbucketCloudAuthType.ACCESS_TOKEN)
            casc(
                """
                    ontrack:
                        config:
                            bitbucket-cloud:
                                - name: ${config2.name}
                                  authType: ACCESS_TOKEN
                                  token: ${config2.token}
                """.trimIndent()
            )
            asAdmin {
                val oldConfig = bitbucketCloudConfigurationService.findConfiguration(config1.name)
                assertNull(oldConfig, "Old config has been removed")
                assertEquals(config2, bitbucketCloudConfigurationService.getConfiguration(config2.name))
            }
        }
    }

    @Test
    fun `Rendering does not expose the tokens`() {
        withDisabledConfigurationTest {
            val config = bitbucketCloudTestConfigMock().copy(autoMergeEmail = "approver@example.com", autoMergeToken = "xxx")
            asAdmin {
                bitbucketCloudConfigurationService.newConfiguration(config)
                assertEquals(
                    """
                        [{
                            "name": "${config.name}",
                            "authType": "API_TOKEN",
                            "email": "user@example.com",
                            "token": "",
                            "autoMergeEmail": "approver@example.com",
                            "autoMergeToken": ""
                        }]
                    """.trimIndent().parseAsJson(),
                    bitbucketCloudConfigurationCascContext.render()
                )
            }
        }
    }

}
