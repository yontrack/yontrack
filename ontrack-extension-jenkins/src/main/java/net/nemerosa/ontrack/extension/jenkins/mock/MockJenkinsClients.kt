package net.nemerosa.ontrack.extension.jenkins.mock

import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The mock Jenkins servers, one per Jenkins configuration name, and what they ran.
 *
 * Shared by the `mock-jenkins` channel, which runs where `ontrack.config.extension.jenkins.mock.enabled`
 * is set, and by [MockJenkinsClientFactory], which stands for every Jenkins client in the `dev`
 * profile - so that the jobs either of them runs are the ones [MockJenkinsController] reports.
 * Unconditional because it is: empty, and nothing reaches it unless one of them is there.
 *
 * In memory, and per backend: the jobs neither survive a restart nor are shared between replicas.
 * What a workflow or a notification makes of a run is recorded in the database by its caller.
 */
@Component
class MockJenkinsClients {

    private val clients = ConcurrentHashMap<String, MockJenkinsClient>()

    fun findClient(config: String): MockJenkinsClient? = clients[config]

    fun getClient(configuration: JenkinsConfiguration): MockJenkinsClient =
        clients.computeIfAbsent(configuration.name) {
            MockJenkinsClient(configuration)
        }

    /**
     * The mock Jenkins named [config], for a channel configuration naming no Jenkins configuration
     * of the instance.
     */
    fun getClient(config: String): MockJenkinsClient =
        getClient(mockConfiguration(config))

    companion object {
        /**
         * Base URL of a mock Jenkins standing for no Jenkins configuration. Never called: it is what
         * the URLs of its jobs and builds read.
         */
        const val MOCK_URL = "https://jenkins.mock"

        fun mockConfiguration(config: String) = JenkinsConfiguration(
            name = config,
            url = "$MOCK_URL/$config",
            user = null,
            password = null,
        )
    }
}
