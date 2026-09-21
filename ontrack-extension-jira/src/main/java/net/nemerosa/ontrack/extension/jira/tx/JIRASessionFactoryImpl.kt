package net.nemerosa.ontrack.extension.jira.tx

import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.client.JIRAClient
import net.nemerosa.ontrack.extension.jira.client.JIRAClient.Companion.PROPERTY_JIRA_CLIENT_TYPE
import net.nemerosa.ontrack.extension.jira.client.JIRAClient.Companion.PROPERTY_JIRA_CLIENT_TYPE_DEFAULT
import net.nemerosa.ontrack.extension.jira.client.JIRAClientImpl
import net.nemerosa.ontrack.extension.support.client.RestTemplateProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = [PROPERTY_JIRA_CLIENT_TYPE],
    havingValue = PROPERTY_JIRA_CLIENT_TYPE_DEFAULT,
    matchIfMissing = true,
)
class JIRASessionFactoryImpl(
    private val restTemplateProvider: RestTemplateProvider,
) : JIRASessionFactory {

    override fun create(configuration: JIRAConfiguration): JIRASession {

        // Creates an HTTP client
        val template = restTemplateProvider.createRestTemplate(
            rootUri = configuration.apiUrl
                ?.takeIf { it.isNotBlank() }
                ?: configuration.url
        ) {
            if (configuration.user.isNullOrBlank()) {
                if (configuration.password.isNullOrBlank()) {
                    this // No authentication
                } else {
                    // Password as a token
                    this.defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${configuration.password}"
                    )
                }
            } else {
                this.basicAuthentication(requireNotNull(configuration.user) { "Username must not be null" }, requireNotNull(configuration.password) { "Password must not be null" })
            }
        }

        // Creates the client
        val client: JIRAClient = JIRAClientImpl(template)

        // Creates the session
        return object : JIRASession {

            override fun getClient(): JIRAClient {
                return client
            }

            override fun close() {
                client.close()
            }
        }
    }
}
