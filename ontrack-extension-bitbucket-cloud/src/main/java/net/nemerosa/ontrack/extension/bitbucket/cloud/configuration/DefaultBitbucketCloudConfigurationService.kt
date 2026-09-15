package net.nemerosa.ontrack.extension.bitbucket.cloud.configuration

import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.support.AbstractConfigurationService
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.ConnectionResult
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DefaultBitbucketCloudConfigurationService(
    configurationRepository: ConfigurationRepository,
    securityService: SecurityService,
    encryptionService: EncryptionService,
    eventPostService: EventPostService,
    eventFactory: EventFactory,
    ontrackConfigProperties: OntrackConfigProperties,
    private val bitbucketCloudClientFactory: BitbucketCloudClientFactory,
) : AbstractConfigurationService<BitbucketCloudConfiguration>(
    BitbucketCloudConfiguration::class.java,
    configurationRepository,
    securityService,
    encryptionService,
    eventPostService,
    eventFactory,
    ontrackConfigProperties
), BitbucketCloudConfigurationService {

    override val type: String = "bitbucket-cloud"

    override fun checkConfigurationFields(configuration: BitbucketCloudConfiguration) {
        configuration.checkFields()
    }

    public override fun validate(configuration: BitbucketCloudConfiguration): ConnectionResult =
        try {
            bitbucketCloudClientFactory.getBitbucketCloudClient(configuration).validate()
            ConnectionResult.ok()
        } catch (any: Exception) {
            ConnectionResult.error(
                "Cannot connect to Bitbucket Cloud using the ${configuration.authType} of the ${configuration.name} configuration: ${any.message}"
            )
        }

}
