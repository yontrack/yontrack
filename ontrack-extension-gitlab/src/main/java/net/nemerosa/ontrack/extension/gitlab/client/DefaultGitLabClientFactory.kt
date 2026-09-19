package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import org.springframework.stereotype.Component

@Component
class DefaultGitLabClientFactory : GitLabClientFactory {

    override fun create(configuration: GitLabConfiguration): GitLabClient =
        DefaultGitLabClient(configuration)

}
