package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.json.getRequiredBooleanField
import net.nemerosa.ontrack.json.getRequiredIntField
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class GQLRootQueryWebhooksIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var webhookAdminService: WebhookAdminService

    @Test
    fun `Get a webhook by name`() {
        val name1 = TestUtils.uid("wh")
        val name2 = TestUtils.uid("wh")
        asAdmin {
            webhookAdminService.createWebhook(
                name = name1,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookFixtures.webhookAuthentication(),
            )
            webhookAdminService.createWebhook(
                name = name2,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookFixtures.webhookAuthentication(),
            )
            run("""{
                webhooks(name: "$name1") {
                    name
                    enabled
                    url
                    timeoutSeconds
                    authenticationType
                }
            }""") { data ->
                assertNotNull(data.path("webhooks").find {
                    it.getRequiredTextField("name") == name1
                }, "Found the requested webhook")
                assertNull(data.path("webhooks").find {
                    it.getRequiredTextField("name") == name2
                }, "Did not find the requested webhook")
            }
        }
    }

    @Test
    fun `Get the list of webhooks`() {
        val name = TestUtils.uid("wh")
        asAdmin {
            webhookAdminService.createWebhook(
                name = name,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookFixtures.webhookAuthentication(),
            )
            run("""{
                webhooks {
                    name
                    enabled
                    url
                    timeoutSeconds
                    authenticationType
                }
            }""") { data ->
                val webhook = data.path("webhooks").find {
                    it.getRequiredTextField("name") == name
                }
                assertNotNull(webhook, "Found the webhook") {
                    assertEquals(name, it.getRequiredTextField("name"))
                    assertEquals(true, it.getRequiredBooleanField("enabled"))
                    assertEquals("uri:test", it.getRequiredTextField("url"))
                    assertEquals(60, it.getRequiredIntField("timeoutSeconds"))
                }
            }
        }
    }

    @Test
    fun `Getting a webhook by name in the list is not granted to a regular user`() {
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
            runWithMatchingError(
                """{
                    webhooks(name: "$name") {
                        name
                        url
                    }
                }""",
                errorMessage = "Global function 'WebhookManagement' is not granted.",
            )
        }
    }

    @Test
    fun `Getting the list of webhooks is not granted to a regular user`() {
        asUser {
            runWithMatchingError(
                """{
                    webhooks {
                        name
                        url
                    }
                }""",
                errorMessage = "Global function 'WebhookManagement' is not granted.",
            )
        }
    }

}
