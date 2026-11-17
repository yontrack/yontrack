package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.SubConfigContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * CasC definition for the list of HTTP configurations, under `ontrack.config.http`.
 */
@Component
class HttpConfigurationCasc(
    private val httpConfigurationService: HttpConfigurationService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(HttpConfigurationCasc::class.java)

    override val field: String = HttpConfigurationServiceImpl.TYPE

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of HTTP configurations",
            items = jsonTypeBuilder.toType(HttpConfiguration::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<HttpConfiguration>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${HttpConfiguration::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        // Gets the list of existing configurations
        val configurations = httpConfigurationService.configurations

        // Synchronization
        syncForward(
            from = items,
            to = configurations,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating HTTP configuration: ${item.name}")
                httpConfigurationService.newConfiguration(item)
            }
            onModification { item, _ ->
                logger.info("Updating HTTP configuration: ${item.name}")
                httpConfigurationService.updateConfiguration(item.name, item)
            }
            onDeletion { existing ->
                logger.info("Deleting HTTP configuration: ${existing.name}")
                httpConfigurationService.deleteConfiguration(existing.name)
            }
        }
    }

    override fun render(): JsonNode = httpConfigurationService
        .configurations
        .map(HttpConfiguration::obfuscate)
        .asJson()
}
