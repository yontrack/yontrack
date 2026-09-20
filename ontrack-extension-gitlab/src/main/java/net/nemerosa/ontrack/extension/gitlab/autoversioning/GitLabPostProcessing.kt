package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Component

/**
 * Auto-versioning post-processing through a GitLab pipeline.
 */
@Component
class GitLabPostProcessing(
    extensionFeature: GitLabExtensionFeature,
    cachedSettingsService: CachedSettingsService,
    gitLabConfigurationService: GitLabConfigurationService,
    gitLabPipelinesService: GitLabPipelinesService,
) : AbstractGitLabPostProcessing(
    extensionFeature = extensionFeature,
    cachedSettingsService = cachedSettingsService,
    gitLabConfigurationService = gitLabConfigurationService,
    gitLabPipelinesService = gitLabPipelinesService,
) {

    override val id: String = "gitlab"

    override val name: String = "GitLab pipeline post processing"
}
