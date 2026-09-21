package net.nemerosa.ontrack.extension.scm.changelog

import tools.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(using = SemanticChangeLogSectionDeserializer::class)
data class SemanticChangeLogSection(
    val type: String,
    val title: String,
)
