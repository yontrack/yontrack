package net.nemerosa.ontrack.extension.artifactory.client

import net.nemerosa.ontrack.extension.artifactory.configuration.ArtifactoryConfiguration
import net.nemerosa.ontrack.extension.support.client.jackson2RestTemplateBuilder
import org.springframework.stereotype.Component

@Component
class ArtifactoryClientFactoryImpl() :
    ArtifactoryClientFactory {

    override fun getClient(configuration: ArtifactoryConfiguration): ArtifactoryClient {
        val restTemplate = jackson2RestTemplateBuilder()
            .rootUri(configuration.url)
            .basicAuthentication(requireNotNull(configuration.user) { "Username must not be null" }, requireNotNull(configuration.password) { "Password must not be null" })
            .build()
        return ArtifactoryClientImpl(restTemplate)
    }
}
