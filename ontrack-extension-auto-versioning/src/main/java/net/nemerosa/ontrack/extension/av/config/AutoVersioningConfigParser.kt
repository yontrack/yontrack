package net.nemerosa.ontrack.extension.av.config

import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.dataformat.yaml.YAMLMapper

object AutoVersioningConfigParser {

    private val yamlFactory = YAMLFactory.builder()
        .configureForJackson2()
        .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
        .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
        .build()

    private val mapper = YAMLMapper.builder(yamlFactory)
        .configureForJackson2()
        .build()

    /**
     * Renders a configuration as YAML, omitting default fields
     */
    fun toYaml(config: AutoVersioningConfig) =
        mapper.writeValueAsString(config)
}