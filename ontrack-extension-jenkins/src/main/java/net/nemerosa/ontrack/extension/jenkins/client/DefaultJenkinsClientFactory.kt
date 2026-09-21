package net.nemerosa.ontrack.extension.jenkins.client

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationProperties
import net.nemerosa.ontrack.extension.support.client.jackson2RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Profile("!${RunProfile.DEV}")
class DefaultJenkinsClientFactory(
    private val jenkinsConfigurationProperties: JenkinsConfigurationProperties,
) : JenkinsClientFactory {

    override fun getClient(configuration: JenkinsConfiguration): JenkinsClient {
        return DefaultJenkinsClient(
            url = configuration.url,
            client = jackson2RestTemplateBuilder()
                .rootUri(configuration.url)
                .basicAuthentication(requireNotNull(configuration.user) { "Username must not be null" }, requireNotNull(configuration.password) { "Password must not be null" })
                .readTimeout(Duration.ofSeconds(jenkinsConfigurationProperties.timeout.toLong()))
                .build()
        )
    }

}
