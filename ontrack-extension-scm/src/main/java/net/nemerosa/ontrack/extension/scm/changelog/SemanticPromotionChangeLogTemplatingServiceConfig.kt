package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.common.api.APIDescription

open class SemanticPromotionChangeLogTemplatingServiceConfig(
    dependencies: List<String> = emptyList(),
    issues: Boolean = false,
    sections: List<SemanticChangeLogSection> = emptyList(),
    exclude: List<String> = emptyList(),
    allQualifiers: Boolean = false,
    defaultQualifierFallback: Boolean = false,
    commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
    @APIDescription("By default, if a previous promotion is not found on the current branch, it'll be looked for in all branches of the projects. Set this parameter to `false` to disable this behaviour.")
    val acrossBranches: Boolean = true,
) : SemanticChangeLogTemplatingServiceConfig(
    dependencies = dependencies,
    issues = issues,
    sections = sections,
    exclude = exclude,
    allQualifiers = allQualifiers,
    defaultQualifierFallback = defaultQualifierFallback,
    commitsMaxLength = commitsMaxLength,
)