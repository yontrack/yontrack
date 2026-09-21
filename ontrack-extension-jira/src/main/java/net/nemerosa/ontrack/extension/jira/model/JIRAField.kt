package net.nemerosa.ontrack.extension.jira.model

import tools.jackson.databind.JsonNode

class JIRAField(
        val id: String,
        val name: String,
        val value: JsonNode
)
