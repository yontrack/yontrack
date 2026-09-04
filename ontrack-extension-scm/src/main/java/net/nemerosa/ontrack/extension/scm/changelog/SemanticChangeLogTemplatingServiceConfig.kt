package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef

open class SemanticChangeLogTemplatingServiceConfig(
    @APIDescription("Must a section for changelog actual issues be present?")
    override val issues: Boolean = false,
    dependencies: List<String> = emptyList(),
    @APIDescription("Mapping types to section titles")
    override val sections: List<SemanticChangeLogSection> = emptyList(),
    @APIDescription("Types to exclude")
    override val exclude: List<String> = emptyList(),
    @APIDescription("Use emojis in the section titles")
    override val emojis: Boolean = false,
    @APIDescription("Loop over all qualifiers for the last level of `dependencies`, including the default one. Qualifiers at `dependencies` take precedence.")
    val allQualifiers: Boolean = false,
    @APIDescription("If a qualifier has no previous link, uses the default qualifier (empty) qualifier.")
    val defaultQualifierFallback: Boolean = false,
    @APIDescription("Maximum length of a commit message in a change log, ellipsis included. Only the first line of a commit message is ever rendered; set this to 0 to render that line in full.")
    override val commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
) : SemanticChangeLogConfig {
    @APIDescription("Comma-separated list of project links to follow one by one for a get deep change log. Each item in the list is either a project name, or a project name and qualifier separated by a colon (:).")
    @ListRef
    val dependencies: List<String> = dependencies.flatMap {
        it.split(",").map { item -> item.trim() }
    }
}
