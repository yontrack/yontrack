package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.changelog.COMMIT_MESSAGE_DEFAULT_MAX_LENGTH
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingCommitsOption
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingServiceConfig

class BuildChangeLogTemplatingSourceConfig(
    empty: String = "",
    dependencies: List<String> = emptyList(),
    title: Boolean = false,
    allQualifiers: Boolean = false,
    defaultQualifierFallback: Boolean = false,
    commitsOption: ChangeLogTemplatingCommitsOption = ChangeLogTemplatingCommitsOption.NONE,
    commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
    @APIDescription("ID to the build to get the change log from")
    val from: Int,
) : ChangeLogTemplatingServiceConfig(
    empty = empty,
    dependencies = dependencies,
    title = title,
    allQualifiers = allQualifiers,
    defaultQualifierFallback = defaultQualifierFallback,
    commitsOption = commitsOption,
    commitsMaxLength = commitsMaxLength,
)
