package net.nemerosa.ontrack.extension.artifactory.client

import net.nemerosa.ontrack.extension.artifactory.configuration.ArtifactoryConfiguration
import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import org.springframework.stereotype.Component

@Component
class ArtifactoryClientFactoryImpl() :
    ArtifactoryClientFactory {

    override fun getClient(configuration: ArtifactoryConfiguration): ArtifactoryClient {
        val restTemplate = restTemplateBuilder()
            .rootUri(configuration.url)
            .basicAuthentication(requireNotNull(configuration.user) { "Username must not be null" }, requireNotNull(configuration.password) { "Password must not be null" })
            .build()
        return ArtifactoryClientImpl(restTemplate)
    }
}
