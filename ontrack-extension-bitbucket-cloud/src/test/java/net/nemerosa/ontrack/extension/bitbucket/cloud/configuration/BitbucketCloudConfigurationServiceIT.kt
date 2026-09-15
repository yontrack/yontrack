package net.nemerosa.ontrack.extension.bitbucket.cloud.configuration

import net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloud
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigReal
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigRealAccessToken
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.ConnectionResultType
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BitbucketCloudConfigurationServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var bitbucketCloudConfigurationService: BitbucketCloudConfigurationService

    @Autowired
    private lateinit var configurationRepository: ConfigurationRepository

    private fun apiTokenConfig(name: String = uid("C")) = BitbucketCloudConfiguration(
        name = name,
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = "bot@example.com",
        token = "xxxx",
        autoMergeEmail = "approver@example.com",
        autoMergeToken = "yyyy",
    )

    @Test
    fun `Creation of an API token configuration`() {
        withDisabledConfigurationTest {
            asAdmin {
                val config = apiTokenConfig()
                val savedConfig = bitbucketCloudConfigurationService.newConfiguration(config)
                assertEquals(config.name, savedConfig.name)
                assertEquals(BitbucketCloudAuthType.API_TOKEN, savedConfig.authType)
                assertEquals("bot@example.com", savedConfig.email)
                assertEquals("", savedConfig.token)
                assertEquals("approver@example.com", savedConfig.autoMergeEmail)
                assertEquals("", savedConfig.autoMergeToken)

                val loaded = bitbucketCloudConfigurationService.getConfiguration(config.name)
                assertEquals(config, loaded, "Tokens are decrypted")

                assertNotNull(
                    bitbucketCloudConfigurationService.configurations.find { it.name == config.name },
                    "Created configuration is in the list"
                )
            }
        }
    }

    @Test
    fun `Tokens are encrypted in the storage`() {
        withDisabledConfigurationTest {
            asAdmin {
                val config = apiTokenConfig()
                bitbucketCloudConfigurationService.newConfiguration(config)
                val stored = configurationRepository.find(BitbucketCloudConfiguration::class.java, config.name)
                assertNotNull(stored) {
                    assertNotEquals("xxxx", it.token)
                    assertNotEquals("yyyy", it.autoMergeToken)
                }
            }
        }
    }

    @Test
    fun `Creation of an access token configuration`() {
        withDisabledConfigurationTest {
            asAdmin {
                val config = bitbucketCloudTestConfigMock(authType = BitbucketCloudAuthType.ACCESS_TOKEN)
                bitbucketCloudConfigurationService.newConfiguration(config)
                val loaded = bitbucketCloudConfigurationService.getConfiguration(config.name)
                assertEquals(BitbucketCloudAuthType.ACCESS_TOKEN, loaded.authType)
                assertNull(loaded.email)
                assertEquals("token", loaded.token)
            }
        }
    }

    @Test
    fun `API token configuration without email is rejected`() {
        withDisabledConfigurationTest {
            asAdmin {
                assertThrows<BitbucketCloudConfigurationMissingFieldException> {
                    bitbucketCloudConfigurationService.newConfiguration(apiTokenConfig().copy(email = null))
                }
            }
        }
    }

    @Test
    fun `Updating a configuration keeps the tokens when not provided`() {
        withDisabledConfigurationTest {
            asAdmin {
                val config = apiTokenConfig()
                bitbucketCloudConfigurationService.newConfiguration(config)

                bitbucketCloudConfigurationService.updateConfiguration(
                    config.name,
                    config.copy(token = "", autoMergeToken = "")
                )

                val savedConfig = bitbucketCloudConfigurationService.getConfiguration(config.name)
                assertEquals(config, savedConfig)
            }
        }
    }

    @Test
    fun `Updating a configuration to another authentication type`() {
        withDisabledConfigurationTest {
            asAdmin {
                val config = apiTokenConfig()
                bitbucketCloudConfigurationService.newConfiguration(config)

                val update = BitbucketCloudConfiguration(
                    name = config.name,
                    authType = BitbucketCloudAuthType.ACCESS_TOKEN,
                    token = "access",
                )
                bitbucketCloudConfigurationService.updateConfiguration(config.name, update)

                assertEquals(update, bitbucketCloudConfigurationService.getConfiguration(config.name))
            }
        }
    }

    @Test
    fun `Deleting a configuration`() {
        withDisabledConfigurationTest {
            asAdmin {
                val config = apiTokenConfig()
                bitbucketCloudConfigurationService.newConfiguration(config)

                bitbucketCloudConfigurationService.deleteConfiguration(config.name)

                assertNull(
                    bitbucketCloudConfigurationService.configurations.find { it.name == config.name },
                    "Deleted configuration cannot be found any longer"
                )
            }
        }
    }

    @Test
    fun `Testing a configuration with an invalid token`() {
        asAdmin {
            val config = bitbucketCloudTestConfigMock(authType = BitbucketCloudAuthType.ACCESS_TOKEN)
            val result = bitbucketCloudConfigurationService.test(config)
            assertEquals(ConnectionResultType.ERROR, result.type)
        }
    }

    @TestOnBitbucketCloud
    fun `Testing a configuration with an API token`() {
        asAdmin {
            val config = bitbucketCloudTestConfigReal()
            val result = bitbucketCloudConfigurationService.test(config)
            assertEquals(ConnectionResultType.OK, result.type, result.message)
        }
    }

    @TestOnBitbucketCloud
    fun `Testing a configuration with an access token`() {
        asAdmin {
            val config = bitbucketCloudTestConfigRealAccessToken()
            val result = bitbucketCloudConfigurationService.test(config)
            assertEquals(ConnectionResultType.OK, result.type, result.message)
        }
    }

    @TestOnBitbucketCloud
    fun `Creating a real configuration with the connection test enabled`() {
        asAdmin {
            val config = bitbucketCloudTestConfigRealAccessToken()
            bitbucketCloudConfigurationService.newConfiguration(config)
            assertEquals(config, bitbucketCloudConfigurationService.getConfiguration(config.name))
            bitbucketCloudConfigurationService.deleteConfiguration(config.name)
        }
    }

}
