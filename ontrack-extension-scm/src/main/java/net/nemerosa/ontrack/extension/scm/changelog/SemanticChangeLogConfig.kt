package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.common.api.APIDescription

interface SemanticChangeLogConfig {

    @APIDescription("Must a section for changelog actual issues be present?")
    val issues: Boolean

    @APIDescription("Mapping types to section titles")
    val sections: List<SemanticChangeLogSection>

    @APIDescription("Types to exclude")
    val exclude: List<String>

    @APIDescription("Use emojis in the section titles")
    val emojis: Boolean

    @APIDescription("Maximum length of a commit message in a change log, ellipsis included. Only the first line of a commit message is ever rendered; set this to 0 to render that line in full.")
    val commitsMaxLength: Int

}
