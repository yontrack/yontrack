package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

internal class WebhookAdminServiceIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var webhookAdminService: WebhookAdminService

    @Test
    fun `Creating a webhook`() {
        val name = uid("wh")
        asAdmin {
            val wh = webhookAdminService.createWebhook(
                name = name,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookFixtures.webhookAuthentication(),
            )
            assertNotNull(webhookAdminService.findWebhookByName(name)) {
                assertEquals(wh, it)
            }
        }
    }

    @Test
    fun `Creating a webhook with basic authentication`() {
        val name = uid("wh")
        asAdmin {
            webhookAdminService.createWebhook(
                name = name,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookAuthentication(
                    type = "basic",
                    config = mapOf(
                        "username" to "user",
                        "password" to "xxxx"
                    ).asJson()
                )
            )
            assertNotNull(webhookAdminService.findWebhookByName(name)) {
                assertEquals("basic", it.authentication.type)
                assertEquals(
                    mapOf(
                        "username" to "user",
                        "password" to "xxxx"
                    ).asJson(),
                    it.authentication.config
                )
            }
        }
    }

    @Test
    fun `Creating a webhook with existing name`() {
        val name = uid("wh")
        asAdmin {
            webhookAdminService.createWebhook(
                name = name,
                enabled = true,
                url = "uri:test",
                timeout = Duration.ofMinutes(1),
                authentication = WebhookFixtures.webhookAuthentication(),
            )
            // Creating a webhook with the same name
            assertFailsWith<WebhookAlreadyExistsException> {
                webhookAdminService.createWebhook(
                    name = name,
                    enabled = true,
                    url = "uri:other",
                    timeout = Duration.ofMinutes(1),
                    authentication = WebhookFixtures.webhookAuthentication(),
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Editing a webhook`() {
        val name = uid("wh")
        webhookAdminService.createWebhook(
            name = name,
            enabled = true,
            url = "uri:test",
            timeout = Duration.ofMinutes(1),
            authentication = WebhookFixtures.webhookAuthentication(value = "xxxx"),
        )
        webhookAdminService.updateWebhook(
            name = name,
            enabled = true,
            url = "uri:test",
            timeout = Duration.ofMinutes(2),
            authentication = WebhookFixtures.webhookAuthentication(value = "yyyy"),
        )
        assertNotNull(webhookAdminService.findWebhookByName(name)) {
            assertEquals(name, it.name)
            assertEquals(true, it.enabled)
            assertEquals("uri:test", it.url)
            assertEquals(Duration.ofMinutes(2), it.timeout)
            assertEquals("header", it.authentication.type)
            assertEquals(
                mapOf(
                    "name" to "X-Ontrack-Token",
                    "value" to "yyyy",
                ).asJson(),
                it.authentication.config
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Editing a webhook with existing credentials`() {
        val name = uid("wh")
        webhookAdminService.createWebhook(
            name = name,
            enabled = true,
            url = "uri:test",
            timeout = Duration.ofMinutes(1),
            authentication = WebhookFixtures.webhookAuthentication(value = "xxxx"),
        )
        webhookAdminService.updateWebhook(
            name = name,
            enabled = false,
            url = "uri:test",
            timeout = Duration.ofMinutes(2),
            authentication = WebhookFixtures.webhookAuthentication(value = ""),
        )
        assertNotNull(webhookAdminService.findWebhookByName(name)) {
            assertEquals(name, it.name)
            assertEquals(false, it.enabled)
            assertEquals("uri:test", it.url)
            assertEquals(Duration.ofMinutes(2), it.timeout)
            assertEquals("header", it.authentication.type)
            assertEquals(
                mapOf(
                    "name" to "X-Ontrack-Token",
                    "value" to "xxxx",
                ).asJson(),
                it.authentication.config
            )
        }
    }

    @Test
    fun `Getting a webhook by name is not granted to a regular user`() {
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
                webhookAdminService.findWebhookByName(name)
            }
        }
    }

}
