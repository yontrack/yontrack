package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredBooleanField
import net.nemerosa.ontrack.json.getRequiredIntField
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class WebhooksMutationsIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var webhookAdminService: WebhookAdminService

    @Test
    fun `Creating a webhook`() {
        val name = TestUtils.uid("wh")
        asAdmin {
            run(
                """
                mutation {
                    createWebhook(input: {
                        name: "$name",
                        enabled: true,
                        url: "uri:test",
                        timeoutSeconds: 30,
                        authenticationType: "header",
                        authenticationConfig: {
                            name: "X-Ontrack-Token",
                            value: "xxxx",
                        }
                    }) {
                        errors {
                            message
                        }
                        webhook {
                            name
                            enabled
                            url
                            timeoutSeconds
                            authenticationType
                        }
                    }
                }
            """
            ) { data ->
                checkGraphQLUserErrors(data, "createWebhook") { payload ->
                    assertJsonNotNull(payload.path("webhook")) {
                        assertEquals(name, getRequiredTextField("name"))
                        assertEquals(true, getRequiredBooleanField("enabled"))
                        assertEquals("uri:test", getRequiredTextField("url"))
                        assertEquals(30, getRequiredIntField("timeoutSeconds"))
                    }
                }
            }
            assertNotNull(webhookAdminService.findWebhookByName(name)) {
                assertEquals(name, it.name)
                assertEquals(true, it.enabled)
                assertEquals("uri:test", it.url)
                assertEquals(Duration.ofSeconds(30), it.timeout)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Deleting a webhook`() {
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
                mutation DeleteWebhook(${'$'}name: String!) {
                    deleteWebhook(input: {name: ${'$'}name}) {
                        errors {
                            message
                        }
                    }
                }
            """,
            mapOf("name" to name)
        ) { data ->
            assertNoUserError(data, "deleteWebhook")
            assertNull(webhookAdminService.findWebhookByName(name), "Webhook has been deleted")
        }
    }

    @Test
    @AsAdminTest
    fun `Editing a webhook`() {
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
                mutation UpdateWebhook {
                    updateWebhook(input: {
                        name: "$name",
                        enabled: true,
                        url: "uri:test",
                        timeoutSeconds: 30,
                        authenticationType: "header",
                        authenticationConfig: {
                            name: "X-Ontrack-Token",
                            value: "yyyy",
                        }
                    }) {
                        errors {
                            message
                        }
                    }
                }
            """
        ) { data ->
            assertNoUserError(data, "updateWebhook")
            assertNotNull(webhookAdminService.findWebhookByName(name)) {
                assertEquals(name, it.name)
                assertEquals(true, it.enabled)
                assertEquals("uri:test", it.url)
                assertEquals(Duration.ofSeconds(30), it.timeout)
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
    }

    @Test
    @AsAdminTest
    fun `Editing a webhook with existing credentials`() {
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
                mutation UpdateWebhook {
                    updateWebhook(input: {
                        name: "$name",
                        enabled: false,
                        url: "uri:test",
                        timeoutSeconds: 90,
                        authenticationType: "header",
                        authenticationConfig: {
                            name: "X-Ontrack-Token",
                            value: "",
                        }
                    }) {
                        errors {
                            message
                        }
                    }
                }
            """
        ) { data ->
            assertNoUserError(data, "updateWebhook")
            assertNotNull(webhookAdminService.findWebhookByName(name)) {
                assertEquals(name, it.name)
                assertEquals(false, it.enabled)
                assertEquals("uri:test", it.url)
                assertEquals(Duration.ofSeconds(90), it.timeout)
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
    }

    @Test
    fun `Testing a webhook is not granted to a regular user`() {
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
                    mutation {
                        testWebhook(input: {name: "$name"}) {
                            errors {
                                message
                            }
                        }
                    }
                """,
                errorMessage = "Global function 'WebhookManagement' is not granted.",
            )
        }
    }

}
