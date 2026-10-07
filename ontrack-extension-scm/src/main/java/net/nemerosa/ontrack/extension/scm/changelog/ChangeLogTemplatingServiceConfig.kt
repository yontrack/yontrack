package net.nemerosa.ontrack.extension.scm.changelog

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig

@JsonIgnoreProperties(ignoreUnknown = true)
open class ChangeLogTemplatingServiceConfig(
    @APIDescription("String to use to render an empty or non existent change log")
    val empty: String = "",
    dependencies: List<String> = emptyList(),
    @APIDescription("Include a title for the change log")
    val title: Boolean = false,
    @APIDescription("Loop over all qualifiers for the last level of `dependencies`, including the default one. Qualifiers at `dependencies` take precedence.")
    val allQualifiers: Boolean = false,
    @APIDescription("If a qualifier has no previous link, uses the default qualifier (empty) qualifier.")
    val defaultQualifierFallback: Boolean = false,
    @APIDescription("Defines how to render commits for a change log")
    val commitsOption: ChangeLogTemplatingCommitsOption = ChangeLogTemplatingCommitsOption.NONE,
    @APIDescription(COMMIT_MESSAGE_MAX_LENGTH_DESCRIPTION)
    val commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
    @APIDescription(CHANGELOG_ASSISTANTS_DESCRIPTION)
    val assistants: Boolean = false,
    @APIDescription(CHANGELOG_ASSISTED_COUNT_DESCRIPTION)
    val assistedCount: Boolean = false,
) {
    @APIDescription("Comma-separated list of project links to follow one by one for a get deep change log. Each item in the list is either a project name, or a project name and qualifier separated by a colon (:).")
    @ListRef
    val dependencies: List<String> = dependencies.flatMap {
        it.split(",").map { item -> item.trim() }
    }
    companion object {
        fun emptyValue(config: TemplatingSourceConfig) =
            config.getString(ChangeLogTemplatingServiceConfig::empty.name) ?: ""
    }
}

/**
 * Description of the `assistants` option of the templated change logs.
 */
const val CHANGELOG_ASSISTANTS_DESCRIPTION =
    "If true, each commit line is followed by the assistants (agent kinds) which helped write the commit, e.g. `(assisted by Claude Code)`, the name of an assistant linking to its agent session when the commit carries one (Markdown and HTML only). Applies only when the commits are rendered (see `commitsOption`)."

/**
 * Description of the `assistedCount` option of the templated change logs.
 */
const val CHANGELOG_ASSISTED_COUNT_DESCRIPTION =
    "If true, the change log starts with the number of its commits written with an assistant, e.g. `3 of 12 commits assisted`. Nothing is added when the change log has no commit."
