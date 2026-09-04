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

    @APIDescription(COMMIT_MESSAGE_MAX_LENGTH_DESCRIPTION)
    val commitsMaxLength: Int

}
