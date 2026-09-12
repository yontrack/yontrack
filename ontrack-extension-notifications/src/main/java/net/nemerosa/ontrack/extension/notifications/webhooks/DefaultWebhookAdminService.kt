package net.nemerosa.ontrack.extension.notifications.webhooks

import com.fasterxml.jackson.databind.node.NullNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
@Transactional
class DefaultWebhookAdminService(
    private val storageService: StorageService,
    private val securityService: SecurityService,
    private val encryptionService: EncryptionService,
    private val webhookAuthenticatorRegistry: WebhookAuthenticatorRegistry,
) : WebhookAdminService {

    override val webhooks: List<Webhook>
        get() {
            securityService.checkGlobalFunction(WebhookManagement::class.java)
            return storageService.getData(STORE, StoredWebhook::class.java).values
                .map { it.toWebhook() }
                .sortedBy { it.name }
        }

    override fun createWebhook(
        name: String,
        enabled: Boolean,
        url: String,
        timeout: Duration,
        authentication: WebhookAuthentication,
    ): Webhook {
        securityService.checkGlobalFunction(WebhookManagement::class.java)
        val existing = findWebhookByName(name)
        if (existing != null) {
            throw WebhookAlreadyExistsException(name)
        }

        val authenticator = webhookAuthenticatorRegistry.findWebhookAuthenticator(authentication.type)
            ?: throw WebhookAuthenticatorNotFoundException(authentication.type)
        authenticator.validateConfig(authentication.config)

        val webhook = StoredWebhook(
            name = name,
            enabled = enabled,
            url = url,
            timeoutSeconds = timeout.toSeconds(),
            authenticationType = authentication.type,
            authenticationEncryptedConfig = encryptionService.encrypt(
                authentication.config.format()
            ) ?: ""
        )
        storageService.store(
            STORE,
            name,
            webhook
        )
        return webhook.toWebhook()
    }

    override fun updateWebhook(
        name: String,
        enabled: Boolean?,
        url: String?,
        timeout: Duration?,
        authentication: WebhookAuthentication?,
    ): Webhook {
        securityService.checkGlobalFunction(WebhookManagement::class.java)
        val existing = findWebhookByName(name) ?: throw WebhookNotFoundException(name)

        // Controlling the authentication
        val actualAuthentication = if (authentication != null) {
            val authenticator = webhookAuthenticatorRegistry.findWebhookAuthenticator(authentication.type)
                ?: throw WebhookAuthenticatorNotFoundException(authentication.type)
            authenticator.validateConfig(authentication.config)
            // Reusing existing authentication
            if (authentication.type == existing.authentication.type) {
                WebhookAuthentication(
                    type = authentication.type,
                    config = authenticator.merge(
                        input = authentication.config,
                        existing = existing.authentication.config
                    ).asJson(),
                )
            } else {
                authentication
            }
        } else {
            existing.authentication
        }

        // New record
        val webhook = StoredWebhook(
            name = name,
            enabled = enabled ?: existing.enabled,
            url = url ?: existing.url,
            timeoutSeconds = timeout?.toSeconds() ?: existing.timeout.toSeconds(),
            authenticationType = authentication?.type ?: existing.authentication.type,
            authenticationEncryptedConfig = encryptionService.encrypt(
                actualAuthentication.config.format()
            ) ?: ""
        )
        storageService.store(
            STORE,
            name,
            webhook
        )

        return webhook.toWebhook()
    }

    override fun deleteWebhook(name: String) {
        securityService.checkGlobalFunction(WebhookManagement::class.java)
        storageService.delete(STORE, name)
    }

    override fun findWebhookByName(name: String): Webhook? {
        securityService.checkGlobalFunction(WebhookManagement::class.java)
        return storageService.find(
            STORE,
            name,
            StoredWebhook::class
        )?.toWebhook()
    }

    private fun StoredWebhook.toWebhook() = Webhook(
        name = name,
        enabled = enabled,
        url = url,
        timeout = Duration.ofSeconds(timeoutSeconds),
        authentication = WebhookAuthentication(
            type = authenticationType,
            config = encryptionService.decrypt(authenticationEncryptedConfig)
                ?.parseAsJson()
                ?: NullNode.instance
        )
    )

    companion object {
        private val STORE = Webhook::class.java.name
    }

    class StoredWebhook(
        val name: String,
        val enabled: Boolean,
        val url: String,
        val timeoutSeconds: Long,
        val authenticationType: String,
        val authenticationEncryptedConfig: String,
    )

}