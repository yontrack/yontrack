package net.nemerosa.ontrack.extension.gitlab.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationLink
import net.nemerosa.ontrack.model.events.EventTemplatingService
import org.springframework.stereotype.Component

@APIDescription("This channel is used to trigger GitLab pipelines on a branch or a tag, with some variables.")
@Documentation(GitLabPipelineNotificationChannelConfig::class)
@Documentation(GitLabPipelineNotificationChannelOutput::class, section = "output")
@DocumentationLink(value = "integrations/notifications/gitlab-pipeline.md", name = "GitLab pipelines")
@NoTemplate
@Component
class GitLabPipelineNotificationChannel(
    gitLabConfigurationService: GitLabConfigurationService,
    eventTemplatingService: EventTemplatingService,
    gitLabPipelinesService: GitLabPipelinesService,
) : AbstractGitLabPipelineNotificationChannel(
    gitLabConfigurationService = gitLabConfigurationService,
    eventTemplatingService = eventTemplatingService,
    gitLabPipelinesService = gitLabPipelinesService,
) {

    override val type: String = "gitlab-pipeline"
    override val displayName: String = "GitLab pipeline"

}
