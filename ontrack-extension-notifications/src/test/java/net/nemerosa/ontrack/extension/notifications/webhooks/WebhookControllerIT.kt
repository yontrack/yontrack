package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import java.time.Duration
import kotlin.test.assertFailsWith

/**
 * Authorization checks on the webhook REST endpoints.
 *
 * Pinging a webhook makes the instance send an outbound request to an admin-configured URL, so it
 * must be restricted to the holders of [WebhookManagement], like every other webhook administration
 * operation.
 */
internal class WebhookControllerIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var webhookController: WebhookController

    @Autowired
    private lateinit var webhookAdminService: WebhookAdminService

    @Test
    fun `Pinging a webhook is not granted to a regular user`() {
        val name = uid("wh")
        asAdmin {
            webhookAdminService.createWebhook(
                name = name,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookFixtures.webhookAuthentication(),
            )
        }
        asUser {
            assertFailsWith<AccessDeniedException> {
                webhookController.pingWebhook(name)
            }
        }
    }

    @Test
    fun `Pinging an unknown webhook is not granted to a regular user either`() {
        val name = uid("wh")
        asUser {
            assertFailsWith<AccessDeniedException> {
                webhookController.pingWebhook(name)
            }
        }
    }

    @Test
    fun `Pinging an unknown webhook as an admin reports it as not found`() {
        val name = uid("wh")
        asAdmin {
            assertFailsWith<WebhookNotFoundException> {
                webhookController.pingWebhook(name)
            }
        }
    }

}
