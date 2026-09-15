package net.nemerosa.ontrack.extension.bitbucket.cloud.configuration

import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloud
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigReal
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigRealAccessToken
import net.nemerosa.ontrack.model.support.ConnectorStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BitbucketCloudConnectorStatusIndicatorIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var bitbucketCloudConnectorStatusIndicator: BitbucketCloudConnectorStatusIndicator

    @TestOnBitbucketCloud
    fun `Connector status indicator OK with an API token`() {
        val config = bitbucketCloudTestConfigReal()
        doTest(config) {
            assertNull(it.error, "Bitbucket Cloud connection OK")
        }
    }

    @TestOnBitbucketCloud
    fun `Connector status indicator OK with an access token`() {
        val config = bitbucketCloudTestConfigRealAccessToken()
        doTest(config) {
            assertNull(it.error, "Bitbucket Cloud connection OK")
        }
    }

    @Test
    fun `Connector status indicator not OK`() {
        val config = bitbucketCloudTestConfigMock()
        doTest(config) {
            assertNotNull(it.error, "Bitbucket Cloud connection NOT OK")
        }
    }

    private fun doTest(config: BitbucketCloudConfiguration, check: (ConnectorStatus) -> Unit) {
        asAdmin {
            withDisabledConfigurationTest {
                bitbucketCloudConfigurationService.newConfiguration(config)
            }
            val statuses = bitbucketCloudConnectorStatusIndicator.statuses
            val status = statuses.find {
                it.description.connector.type == "bitbucket-cloud" &&
                        it.description.connector.name == config.name &&
                        it.description.connection == BitbucketCloudConnectorStatusIndicator.CONNECTION
            }
            assertNotNull(status) {
                check(it)
            }
        }
    }

}
