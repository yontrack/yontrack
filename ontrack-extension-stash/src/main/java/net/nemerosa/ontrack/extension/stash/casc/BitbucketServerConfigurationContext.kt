package net.nemerosa.ontrack.extension.stash.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.SubConfigContext
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * CasC definition for the list of Bitbucket Server configurations.
 */
@Component
class BitbucketServerConfigurationContext(
    private val stashConfigurationService: StashConfigurationService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(BitbucketServerConfigurationContext::class.java)

    override val field: String = "bitbucketServer"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of Bitbucket Server configurations",
            items = jsonTypeBuilder.toType(StashConfiguration::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parseItem()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${StashConfiguration::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        // Gets the list of existing configurations
        val configurations = stashConfigurationService.configurations

        // Synchronization
        syncForward(
            from = items,
            to = configurations,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating Bitbucket Server configuration: ${item.name}")
                stashConfigurationService.newConfiguration(item)
            }
            onModification { item, _ ->
                logger.info("Updating Bitbucket Server configuration: ${item.name}")
                stashConfigurationService.updateConfiguration(item.name, item)
            }
            onDeletion { existing ->
                logger.info("Deleting Bitbucket Server configuration: ${existing.name}")
                stashConfigurationService.deleteConfiguration(existing.name)
            }
        }
    }

    override fun render(): JsonNode = stashConfigurationService
        .configurations
        .map(StashConfiguration::obfuscate)
        .asJson()

    private fun JsonNode.parseItem(): StashConfiguration =
        StashConfiguration(
            name = getRequiredTextField("name"),
            url = getRequiredTextField("url"),
            user = getRequiredTextField("user"),
            password = getRequiredTextField("password"),
            autoMergeUser = getTextField("autoMergeUser"),
            autoMergeToken = getTextField("autoMergeToken"),
        )
}