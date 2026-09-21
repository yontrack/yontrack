package net.nemerosa.ontrack.extension.gitlab.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.SubConfigContext
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
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
class GitLabConfigurationCascContext(
    private val gitLabConfigurationService: GitLabConfigurationService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(GitLabConfigurationCascContext::class.java)

    override val field: String = "gitlab"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of GitLab configurations",
            items = jsonTypeBuilder.toType(GitLabConfigurationCascData::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<GitLabConfigurationCascData>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${GitLabConfiguration::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        // Gets the list of existing configurations
        val configs: List<GitLabConfiguration> = gitLabConfigurationService.configurations

        // Synchronization
        syncForward(
            from = items,
            to = configs,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating GitLab configuration: ${item.name}")
                gitLabConfigurationService.newConfiguration(item.toConfiguration())
            }
            onModification { item, _ ->
                logger.info("Updating GitLab configuration: ${item.name}")
                gitLabConfigurationService.updateConfiguration(item.name, item.toConfiguration())
            }
            onDeletion { existing ->
                logger.info("Deleting GitLab configuration: ${existing.name}")
                gitLabConfigurationService.deleteConfiguration(existing.name)
            }
        }
    }

    override fun render(): JsonNode = gitLabConfigurationService.configurations.map {
        GitLabConfigurationCascData(
            name = it.name,
            url = it.url,
            token = "",
            ignoreSslCertificate = it.ignoreSslCertificate,
        )
    }.asJson()

    data class GitLabConfigurationCascData(
        @APIDescription("Name of the configuration")
        val name: String,
        @APIDescription("URL of the GitLab instance, like https://gitlab.com")
        val url: String,
        @APIDescription("Personal access token used by Yontrack to connect to GitLab, with the `api` scope")
        val token: String,
        @APIDescription("Accepts any SSL certificate, for a self-managed instance behind an internal certificate authority")
        val ignoreSslCertificate: Boolean = false,
    ) {
        fun toConfiguration() = GitLabConfiguration(
            name = name,
            url = url,
            token = token,
            ignoreSslCertificate = ignoreSslCertificate,
        )
    }

}
