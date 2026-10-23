package net.nemerosa.ontrack.extension.jenkins.mock

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.extension.jenkins.JenkinsExtensionConfigProperties
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClient
import net.nemerosa.ontrack.extension.jenkins.notifications.AbstractJenkinsNotificationChannel
import net.nemerosa.ontrack.extension.jenkins.notifications.JenkinsNotificationChannelConfig
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.EventTemplatingService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = [JenkinsExtensionConfigProperties.MOCK_ENABLED], havingValue = "true", matchIfMissing = false)
@APIDescription(
    "Runs a Jenkins job in memory, without calling any Jenkins: the build succeeds, or fails when the " +
            "job is given a `result` parameter of `FAILURE`, after `waiting` milliseconds if set. " +
            "Intended for demonstrations and testing - do not enable on an instance tracking real deliveries."
)
@Documentation(JenkinsNotificationChannelConfig::class)
class MockJenkinsNotificationChannel(
    private val jenkinsConfigurationService: JenkinsConfigurationService,
    eventTemplatingService: EventTemplatingService,
    private val mockJenkinsClients: MockJenkinsClients,
) : AbstractJenkinsNotificationChannel(
    jenkinsConfigurationService,
    eventTemplatingService,
) {

    override val type: String = "mock-jenkins"

    override val displayName: String = "Mock Jenkins"

    /**
     * A Jenkins configuration of that name when the instance has one, and the mock Jenkins of that
     * name otherwise: outside of the `dev` profile, the connection test of a Jenkins configuration
     * calls a real Jenkins, so an instance demonstrating the mock could not create one for it.
     */
    override fun findJenkinsConfiguration(name: String): JenkinsConfiguration =
        jenkinsConfigurationService.findConfiguration(name)
            ?: MockJenkinsClients.mockConfiguration(name)

    override fun createJenkinsClient(config: JenkinsConfiguration): JenkinsClient =
        mockJenkinsClients.getClient(config)

}
