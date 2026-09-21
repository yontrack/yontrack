package net.nemerosa.ontrack.extension.av.properties

import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper

abstract class AbstractJsonFilePropertyType : AbstractTextFilePropertyType() {

    private val mapper = JsonMapper.builderWithJackson2Defaults()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    override fun readProperty(content: String, targetProperty: String?): String? {
        val json = mapper.readTree(content)
        return readProperty(json, targetProperty)
    }

    abstract fun readProperty(content: JsonNode, targetProperty: String?): String?

    override fun replaceProperty(content: String, targetProperty: String?, targetVersion: String): String {
        val json = mapper.readTree(content)
        val output = replaceProperty(json, targetProperty, targetVersion)
        return mapper.writeValueAsString(output)
    }

    abstract fun replaceProperty(content: JsonNode, targetProperty: String?, targetVersion: String): JsonNode
}