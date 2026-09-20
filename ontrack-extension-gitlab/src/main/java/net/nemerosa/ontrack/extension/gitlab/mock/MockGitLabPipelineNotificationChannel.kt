package net.nemerosa.ontrack.extension.gitlab.mock

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.gitlab.notifications.AbstractGitLabPipelineNotificationChannel
import net.nemerosa.ontrack.extension.gitlab.notifications.GitLabPipelineNotificationChannelConfig
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.EventTemplatingService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Same configuration as the `gitlab-pipeline` channel, but the pipelines are only recorded, and their
 * outcome is driven by the `MOCK_STATUS` and `MOCK_DURATION_SECONDS` variables. See [MockGitLabPipelineRun].
 */
@Component
@Profile(RunProfile.DEV)
@Documentation(GitLabPipelineNotificationChannelConfig::class)
@NoTemplate
class MockGitLabPipelineNotificationChannel(
    gitLabConfigurationService: GitLabConfigurationService,
    eventTemplatingService: EventTemplatingService,
    mockGitLabPipelinesRecorder: MockGitLabPipelinesRecorder,
) : AbstractGitLabPipelineNotificationChannel(
    gitLabConfigurationService = gitLabConfigurationService,
    eventTemplatingService = eventTemplatingService,
    gitLabPipelinesService = MockGitLabPipelinesService(mockGitLabPipelinesRecorder),
) {

    override val type: String = "mock-gitlab-pipeline"
    override val displayName: String = "Mock GitLab pipeline"

}
