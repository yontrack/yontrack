package net.nemerosa.ontrack.extension.bitbucket.cloud.casc

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
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

@Component
class BitbucketCloudConfigurationCascContext(
    private val bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(BitbucketCloudConfigurationCascContext::class.java)

    override val field: String = "bitbucket-cloud"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of Bitbucket Cloud configurations",
            items = jsonTypeBuilder.toType(BitbucketCloudConfigurationCascData::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<BitbucketCloudConfigurationCascData>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${BitbucketCloudConfiguration::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        // Gets the list of existing configurations
        val configs: List<BitbucketCloudConfiguration> = bitbucketCloudConfigurationService.configurations

        // Synchronization
        syncForward(
            from = items,
            to = configs,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating Bitbucket Cloud configuration: ${item.name}")
                bitbucketCloudConfigurationService.newConfiguration(item.toConfiguration())
            }
            onModification { item, _ ->
                logger.info("Updating Bitbucket Cloud configuration: ${item.name}")
                bitbucketCloudConfigurationService.updateConfiguration(item.name, item.toConfiguration())
            }
            onDeletion { existing ->
                logger.info("Deleting Bitbucket Cloud configuration: ${existing.name}")
                bitbucketCloudConfigurationService.deleteConfiguration(existing.name)
            }
        }
    }

    override fun render(): JsonNode = bitbucketCloudConfigurationService.configurations.map {
        BitbucketCloudConfigurationCascData(
            name = it.name,
            authType = it.authType,
            email = it.email,
            token = "",
            autoMergeEmail = it.autoMergeEmail,
            autoMergeToken = it.autoMergeToken?.let { "" },
        )
    }.asJson()

    data class BitbucketCloudConfigurationCascData(
        @APIDescription("Name of the configuration")
        val name: String,
        @APIDescription("Type of authentication: API_TOKEN (Atlassian account email + API token, every plan) or ACCESS_TOKEN (workspace, project or repository access token, used as a Bearer token)")
        val authType: BitbucketCloudAuthType,
        @APIDescription("Atlassian account email, required for the API_TOKEN authentication type")
        val email: String? = null,
        @APIDescription("API token or access token")
        val token: String,
        @APIDescription("Atlassian account email of the identity approving pull requests for auto-versioning")
        val autoMergeEmail: String? = null,
        @APIDescription("API token of the identity approving pull requests for auto-versioning")
        val autoMergeToken: String? = null,
    ) {
        fun toConfiguration() = BitbucketCloudConfiguration(
            name = name,
            authType = authType,
            email = email,
            token = token,
            autoMergeEmail = autoMergeEmail,
            autoMergeToken = autoMergeToken,
        )
    }

}
