package net.nemerosa.ontrack.yaml

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValues
import java.io.StringWriter

class Yaml {

    private val yamlFactory = YAMLFactory().apply {
        enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
        // The document start marker is written explicitly, as a separator only (see [write])
        disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    }

    private val mapper = ObjectMapper(yamlFactory).apply {
        registerModule(KotlinModule.Builder().build())
    }

    /**
     * Reads some Yaml as a list of documents
     */
    fun read(content: String): List<JsonNode> {
        val parser = yamlFactory.createParser(content)
        return mapper
            .readValues<JsonNode>(parser)
            .readAll()
    }

    /**
     * Writes a list of documents. Documents are separated by a `---` marker, which is
     * therefore absent when writing one single document.
     */
    fun write(json: List<JsonNode>): String {
        val writer = StringWriter()
        json.forEachIndexed { index, node ->
            if (index > 0) {
                writer.append("---\n")
            }
            val generator = yamlFactory.createGenerator(writer)
            generator.writeObject(node)
            writer.append('\n')
        }
        return writer.toString()
    }

}