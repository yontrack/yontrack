package net.nemerosa.ontrack.extension.github.ingestion.config.parser

import tools.jackson.dataformat.yaml.YAMLFactory
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig.Companion.V2_VERSION
import net.nemerosa.ontrack.json.getTextField
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.dataformat.yaml.YAMLMapper

object ConfigParser {

    private const val FIELD_VERSION = "version"

    private val mapper = YAMLMapper.builder(
        YAMLFactory.builder()
            .configureForJackson2()
            .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
            .build()
    )
        .configureForJackson2()
        .build()

    fun parseYaml(yaml: String): IngestionConfig =
        try {
            val json = mapper.readTree(yaml)
            if (json == null || json.isMissingNode || json.isNull) {
                // Empty document
                IngestionConfig()
            } else {
                // Only the V2 format is supported, and a document without a version is read as V2
                val version = json.getTextField(FIELD_VERSION)
                if (version.isNullOrBlank() || version == V2_VERSION) {
                    ConfigV2Parser.parse(json)
                } else {
                    throw ConfigVersionException(version)
                }
            }
        } catch (ex: Exception) {
            throw ConfigParsingException(ex)
        }

    /**
     * Renders the [config][ingestion config] as a YAML document.
     */
    fun toYaml(config: IngestionConfig): String =
        mapper.writeValueAsString(config)

}