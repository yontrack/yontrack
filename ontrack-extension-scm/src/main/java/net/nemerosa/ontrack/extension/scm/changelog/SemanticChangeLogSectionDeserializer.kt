package net.nemerosa.ontrack.extension.scm.changelog

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode

class SemanticChangeLogSectionDeserializer : ValueDeserializer<SemanticChangeLogSection>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SemanticChangeLogSection {
        val node: JsonNode = p.readValueAsTree()
        return if (node is StringNode) {
            val text = node.stringValue()
            if (text.contains("=")) {
                val type = text.substringBefore("=")
                val title = text.substringAfter("=")
                SemanticChangeLogSection(type, title)
            } else {
                SemanticChangeLogSection(text, text)
            }
        } else if (node is ObjectNode) {
            val type = node.get("type")?.stringValueOpt()?.orElse(null) ?: ""
            val title = node.get("title")?.stringValueOpt()?.orElse(null) ?: ""
            SemanticChangeLogSection(type, title)
        } else {
            throw IllegalArgumentException("Unsupported JSON node type for SemanticChangeLogSection: $node")
        }
    }
}
