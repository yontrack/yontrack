package net.nemerosa.ontrack.extension.gitlab.mock

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.autoversioning.AbstractGitLabPostProcessing
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Same configuration and settings as the `gitlab` post-processing, but the pipelines are only recorded by the
 * [MockGitLabPipelinesRecorder], for the acceptance tests. Nothing is committed to the upgrade branch.
 */
@Component
@Profile(RunProfile.DEV)
class MockGitLabPostProcessing(
    extensionFeature: GitLabExtensionFeature,
    cachedSettingsService: CachedSettingsService,
    gitLabConfigurationService: GitLabConfigurationService,
    mockGitLabPipelinesRecorder: MockGitLabPipelinesRecorder,
) : AbstractGitLabPostProcessing(
    extensionFeature = extensionFeature,
    cachedSettingsService = cachedSettingsService,
    gitLabConfigurationService = gitLabConfigurationService,
    gitLabPipelinesService = MockGitLabPipelinesService(mockGitLabPipelinesRecorder),
) {

    override val id: String = "mock-gitlab"

    override val name: String = "Mock GitLab pipeline post processing"
}
