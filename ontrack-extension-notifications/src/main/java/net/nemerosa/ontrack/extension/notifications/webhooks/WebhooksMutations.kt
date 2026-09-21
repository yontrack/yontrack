package net.nemerosa.ontrack.extension.notifications.webhooks

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class WebhooksMutations(
    private val webhookAdminService: WebhookAdminService,
    private val webhookExecutionService: WebhookExecutionService,
) : TypedMutationProvider() {
    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "createWebhook",
            description = "Registers a webhook",
            input = CreateWebhookInput::class,
            outputName = "webhook",
            outputDescription = "Registered webhook",
            outputType = Webhook::class,
        ) { input ->
            webhookAdminService.createWebhook(
                name = input.name,
                enabled = input.enabled,
                url = input.url,
                timeout = Duration.ofSeconds(input.timeoutSeconds),
                authentication = WebhookAuthentication(
                    type = input.authenticationType,
                    config = input.authenticationConfig,
                )
            )
        },
        unitMutation(
            name = "updateWebhook",
            description = "Updating an existing webhook",
            input = UpdateWebhookInput::class,
        ) { input ->
            webhookAdminService.updateWebhook(
                name = input.name,
                enabled = input.enabled,
                url = input.url,
                timeout = Duration.ofSeconds(input.timeoutSeconds),
                authentication = WebhookAuthentication(
                    type = input.authenticationType,
                    config = input.authenticationConfig,
                )
            )
        },
        unitMutation<DeleteWebhookInput>(
            name = "deleteWebhook",
            description = "Deleting an existing webhook"
        ) { input ->
            webhookAdminService.deleteWebhook(input.name)
        },
        unitMutation<TestWebhookInput>(
            name = "testWebhook",
            description = "Tests a webhook"
        ) { input ->
            val webhook = webhookAdminService.findWebhookByName(input.name)
                ?: throw WebhookNotFoundException(input.name)
            val payload = WebhookPingPayloadData.pingPayload("Webhook ${input.name} ping")
            webhookExecutionService.send(webhook, payload)
        }
    )
}

data class CreateWebhookInput(
    val name: String,
    val enabled: Boolean,
    val url: String,
    val timeoutSeconds: Long,
    val authenticationType: String,
    val authenticationConfig: JsonNode,
)

data class UpdateWebhookInput(
    val name: String,
    val enabled: Boolean,
    val url: String,
    val timeoutSeconds: Long,
    val authenticationType: String,
    val authenticationConfig: JsonNode,
)

data class TestWebhookInput(
    @APIDescription("Name of the webhook to test")
    val name: String,
)

data class DeleteWebhookInput(
    @APIDescription("Name of the webhook to delete")
    val name: String,
)
