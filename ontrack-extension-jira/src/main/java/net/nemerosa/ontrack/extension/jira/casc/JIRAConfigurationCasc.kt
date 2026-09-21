package net.nemerosa.ontrack.extension.jira.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.SubConfigContext
import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
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

/**
 * CasC definition for the list of JIRA configurations.
 */
@Component
class JIRAConfigurationCasc(
    private val jiraConfigurationService: JIRAConfigurationService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(JIRAConfigurationCasc::class.java)

    override val field: String = "jira"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of JIRA configurations",
            items = jsonTypeBuilder.toType(JIRAConfiguration::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<JIRAConfiguration>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${JIRAConfiguration::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        // Gets the list of existing configurations
        val configurations = jiraConfigurationService.configurations

        // Synchronization
        syncForward(
            from = items,
            to = configurations,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating JIRA configuration: ${item.name}")
                jiraConfigurationService.newConfiguration(item)
            }
            onModification { item, _ ->
                logger.info("Updating JIRA configuration: ${item.name}")
                jiraConfigurationService.updateConfiguration(item.name, item)
            }
            onDeletion { existing ->
                logger.info("Deleting JIRA configuration: ${existing.name}")
                jiraConfigurationService.deleteConfiguration(existing.name)
            }
        }
    }

    override fun render(): JsonNode = jiraConfigurationService
        .configurations
        .map(JIRAConfiguration::obfuscate)
        .asJson()

}