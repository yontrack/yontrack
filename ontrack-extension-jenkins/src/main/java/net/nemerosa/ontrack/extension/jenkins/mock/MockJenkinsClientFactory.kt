package net.nemerosa.ontrack.extension.jenkins.mock

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClient
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClientFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * In the `dev` profile, every Jenkins client is a mock one - the `jenkins` channel's, the
 * configurations' connection test, the post-processing. Only there: outside of it, the
 * `mock-jenkins` channel uses [MockJenkinsClients] directly, and leaves the real Jenkins alone.
 */
@Component
@Profile(RunProfile.DEV)
class MockJenkinsClientFactory(
    private val mockJenkinsClients: MockJenkinsClients,
) : JenkinsClientFactory {

    override fun getClient(configuration: JenkinsConfiguration): JenkinsClient =
        mockJenkinsClients.getClient(configuration)

}
