package net.nemerosa.ontrack.extension.github.client

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.git.GitConfigProperties
import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.model.checkGitHubPasswordAuthentication
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = [OntrackGitHubClient.PROPERTY_GITHUB_CLIENT_TYPE],
    havingValue = OntrackGitHubClient.PROPERTY_GITHUB_CLIENT_TYPE_DEFAULT,
    matchIfMissing = true,
)
class DefaultGitHubClientFactoryImpl(
    private val gitHubAppTokenService: GitHubAppTokenService,
    private val gitConfigProperties: GitConfigProperties,
    private val gitConfigService: GitConfigService,
    private val meterRegistry: MeterRegistry,
    private val deprecationService: DeprecationService,
) : OntrackGitHubClientFactory {
    override fun create(configuration: GitHubEngineConfiguration): OntrackGitHubClient {
        deprecationService.checkGitHubPasswordAuthentication(configuration)
        return DefaultOntrackGitHubClient(
            configuration = configuration,
            gitHubAppTokenService = gitHubAppTokenService,
            timeout = gitConfigProperties.remote.timeout,
            retries = gitConfigProperties.remote.retries,
            interval = gitConfigProperties.remote.interval,
            gitConnectionConfig = gitConfigService.gitConnectionConfig,
            meterRegistry = meterRegistry,
        )
    }
}