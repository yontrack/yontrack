package net.nemerosa.ontrack.extension.notifications.webhooks

import com.fasterxml.jackson.annotation.JsonAlias
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
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
import java.time.Duration

@Component
class WebhooksCascConfigContext(
    private val webhookAdminService: WebhookAdminService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(WebhooksCascConfigContext::class.java)

    override val field: String = "webhooks"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of webhooks",
            items = jsonTypeBuilder.toType(CascWebhook::class),
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<CascWebhook>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${CascWebhook::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

        // Gets the list of existing items
        val existing = webhookAdminService.webhooks.map { it.toCasc() }

        // Synchronization
        syncForward(
            from = items,
            to = existing,
        ) {
            equality { a, b -> a.name == b.name }
            onCreation { item ->
                logger.info("Creating webhook: ${item.name}")
                webhookAdminService.createWebhook(
                    name = item.name,
                    enabled = item.enabled,
                    url = item.url,
                    timeout = Duration.ofSeconds(item.timeoutSeconds.toLong()),
                    authentication = WebhookAuthentication(
                        type = item.authentication.type,
                        config = item.authentication.config,
                    )
                )
            }
            onModification { item, _ ->
                logger.info("Updating webhook: ${item.name}")
                webhookAdminService.updateWebhook(
                    name = item.name,
                    enabled = item.enabled,
                    url = item.url,
                    timeout = Duration.ofSeconds(item.timeoutSeconds.toLong()),
                    authentication = WebhookAuthentication(
                        type = item.authentication.type,
                        config = item.authentication.config,
                    )
                )
            }
            onDeletion { existing ->
                logger.info("Deleting webhook: ${existing.name}")
                webhookAdminService.deleteWebhook(existing.name)
            }
        }
    }

    override fun render(): JsonNode =
        webhookAdminService.webhooks.map { it.toCasc() }.asJson()

    private fun Webhook.toCasc() = CascWebhook(
        name = name,
        enabled = enabled,
        url = url,
        timeoutSeconds = timeout.toSeconds().toInt(),
        authentication = CascWebhookAuthentication(
            type = authentication.type,
            config = authentication.config,
        )
    )

    @APIDescription("Webhook registration")
    data class CascWebhook(
        @APIDescription("Webhook unique name")
        val name: String,
        @APIDescription("Webhook enabled or not")
        val enabled: Boolean = true,
        @APIDescription("Webhook endpoint")
        val url: String,
        @APIDescription("Webhook execution timeout (in seconds)")
        @JsonAlias("timeout-seconds")
        val timeoutSeconds: Int,
        @APIDescription("Webhook authentication")
        val authentication: CascWebhookAuthentication,
    )

    @APIDescription("Webhook authentication")
    data class CascWebhookAuthentication(
        @APIDescription("Authentication type: basic, header, bearer, ...")
        val type: String,
        @APIDescription("Authentication configuration (JSON)")
        val config: JsonNode,
    )

}