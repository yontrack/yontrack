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
    @APIDescription("Maximum length of a commit message in a change log, ellipsis included. Only the first line of a commit message is ever rendered; set this to 0 to render that line in full.")
    val commitsMaxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
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