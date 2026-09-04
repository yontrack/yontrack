package net.nemerosa.ontrack.extension.environments.templating

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.scm.changelog.COMMIT_MESSAGE_DEFAULT_MAX_LENGTH
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingCommitsOption
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingServiceConfig

class ChangelogDeploymentTemplatingContextConfig(
    @APIDescription("Status to use when looking for the previous deployment")
    val since: SlotPipelineStatus = SlotPipelineStatus.DONE,
    empty: String = "",
    dependencies: List<String> = emptyList(),
    title: Boolean = false,
    allQualifiers: Boolean = false,
    defaultQualifierFallback: Boolean = false,
    commitsOption: ChangeLogTemplatingCommitsOption = ChangeLogTemplatingCommitsOption.NONE,
    commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
) : ChangeLogTemplatingServiceConfig(
    empty = empty,
    dependencies = dependencies,
    title = title,
    allQualifiers = allQualifiers,
    defaultQualifierFallback = defaultQualifierFallback,
    commitsOption = commitsOption,
    commitsMaxLength = commitsMaxLength,
)
