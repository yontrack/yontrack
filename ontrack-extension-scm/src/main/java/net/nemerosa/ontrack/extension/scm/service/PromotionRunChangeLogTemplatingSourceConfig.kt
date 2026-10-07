package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.extension.scm.changelog.COMMIT_MESSAGE_DEFAULT_MAX_LENGTH
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingCommitsOption
import net.nemerosa.ontrack.extension.scm.changelog.PromotionChangeLogTemplatingServiceConfig

class PromotionRunChangeLogTemplatingSourceConfig(
    empty: String = "",
    dependencies: List<String> = emptyList(),
    title: Boolean = false,
    allQualifiers: Boolean = false,
    defaultQualifierFallback: Boolean = false,
    commitsOption: ChangeLogTemplatingCommitsOption = ChangeLogTemplatingCommitsOption.NONE,
    commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
    assistants: Boolean = false,
    assistedCount: Boolean = false,
    acrossBranches: Boolean = true,
) : PromotionChangeLogTemplatingServiceConfig(
    empty = empty,
    dependencies = dependencies,
    title = title,
    allQualifiers = allQualifiers,
    defaultQualifierFallback = defaultQualifierFallback,
    commitsOption = commitsOption,
    commitsMaxLength = commitsMaxLength,
    assistants = assistants,
    assistedCount = assistedCount,
    acrossBranches = acrossBranches,
)
