package net.nemerosa.ontrack.yaml

import tools.jackson.databind.JsonNode
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValues
import java.io.StringWriter

class Yaml {

    private val yamlFactory = YAMLFactory.builder()
        .configureForJackson2()
        .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
        // The document start marker is written explicitly, as a separator only (see [write])
        .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
        .build()

    private val mapper = YAMLMapper.builder(yamlFactory)
        .configureForJackson2()
        .addModule(KotlinModule.Builder().build())
        .build()

    /**
     * Reads some Yaml as a list of documents
     */
    fun read(content: String): List<JsonNode> {
        val parser = mapper.createParser(content)
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
            mapper.createGenerator(writer).use { generator ->
                generator.writeTree(node)
            }
            writer.append('\n')
        }
        return writer.toString()
    }

}