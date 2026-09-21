package net.nemerosa.ontrack.extension.github.ingestion.config.parser

import tools.jackson.dataformat.yaml.YAMLFactory
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig.Companion.V1_VERSION
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
            // Gets the version from the JSON
            val version = json.getTextField(FIELD_VERSION)
            // Gets the parser from the version
            val parser: JsonConfigParser = when {
                version == V1_VERSION -> ConfigV1Parser
                version == V2_VERSION -> ConfigV2Parser
                version.isNullOrBlank() -> ConfigOldParser
                else -> throw ConfigVersionException(version)
            }
            // Parsing
            parser.parse(json)
        } catch (ex: Exception) {
            throw ConfigParsingException(ex)
        }

    /**
     * Renders the [config][ingestion config] as a YAML document.
     */
    fun toYaml(config: IngestionConfig): String =
        mapper.writeValueAsString(config)

}