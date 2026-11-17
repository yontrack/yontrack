package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.extension.support.AbstractConfigurationService
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.ConnectionResult
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.stereotype.Service

@Service
class HttpConfigurationServiceImpl(
    configurationRepository: ConfigurationRepository,
    securityService: SecurityService,
    encryptionService: EncryptionService,
    eventPostService: EventPostService,
    eventFactory: EventFactory,
    ontrackConfigProperties: OntrackConfigProperties,
    private val httpConfigurationConnectionChecker: HttpConfigurationConnectionChecker,
) : AbstractConfigurationService<HttpConfiguration>(
    HttpConfiguration::class.java,
    configurationRepository,
    securityService,
    encryptionService,
    eventPostService,
    eventFactory,
    ontrackConfigProperties
), HttpConfigurationService {

    override val type: String = TYPE

    override fun checkConfigurationFields(configuration: HttpConfiguration) {
        configuration.check()
    }

    override fun validate(configuration: HttpConfiguration): ConnectionResult =
        httpConfigurationConnectionChecker.check(configuration)

    companion object {
        /**
         * Type of the HTTP configurations, in the generic configuration API and in CasC.
         */
        const val TYPE = "http"
    }

}
