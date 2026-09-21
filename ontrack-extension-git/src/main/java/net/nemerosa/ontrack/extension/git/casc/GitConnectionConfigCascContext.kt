package net.nemerosa.ontrack.extension.git.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.git.support.GitConnectionConfig
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GitConnectionConfigCascContext(
    private val gitConfigService: GitConfigService,
) : AbstractCascContext(), GitConfigSubCascContext {

    private val logger: Logger = LoggerFactory.getLogger(GitConnectionConfigCascContext::class.java)

    override fun run(node: JsonNode, paths: List<String>) {
        val config = node.parseOrNull<GitConnectionConfig>()
        if (config == null) {
            logger.error("Cannot parse Git config from ${path(paths)}")
        } else {
            gitConfigService.saveGitConnectionConfig(config)
        }
    }

    override fun render(): JsonNode = gitConfigService.gitConnectionConfig.asJson()

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitConnectionConfig::class)

    override val field: String = "retryConfiguration"

}