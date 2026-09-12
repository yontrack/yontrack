package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals

class GQLRootQueryWebhookByNameIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var webhookAdminService: WebhookAdminService

    @Test
    @AsAdminTest
    fun `Getting a webhook by name`() {
        val name = TestUtils.uid("wh")
        webhookAdminService.createWebhook(
            name = name,
            enabled = true,
            url = "uri:test",
            timeout = Duration.ofMinutes(1),
            authentication = WebhookFixtures.webhookAuthentication(),
        )
        run(
            """
                query WebhookByName {
                    webhookByName(name: "$name") {
                        name
                        enabled
                        url
                        timeoutSeconds
                        authenticationType
                    }
                }
            """
        ) { data ->
            val webhook = data.path("webhookByName")
            assertEquals(name, webhook.getRequiredTextField("name"))
        }
    }

    @Test
    @AsAdminTest
    fun `Getting a non existing webhook by name`() {
        val name = TestUtils.uid("wh")
        run(
            """
                query WebhookByName {
                    webhookByName(name: "$name") {
                        name
                        enabled
                        url
                        timeoutSeconds
                        authenticationType
                    }
                }
            """
        ) { data ->
            val webhook = data.path("webhookByName")
            assertJsonNull(webhook)
        }
    }

    @Test
    fun `Getting a webhook by name is not granted to a regular user`() {
        val name = TestUtils.uid("wh")
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
            runWithError(
                """
                    query WebhookByName {
                        webhookByName(name: "$name") {
                            name
                            url
                        }
                    }
                """,
                errorMessage = "Global function 'WebhookManagement' is not granted.",
            )
        }
    }

}
