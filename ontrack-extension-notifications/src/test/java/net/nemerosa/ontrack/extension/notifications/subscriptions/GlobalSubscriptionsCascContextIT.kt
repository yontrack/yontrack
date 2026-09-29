package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlobalSubscriptionsCascContextIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @Autowired
    private lateinit var globalSubscriptionsCascContext: GlobalSubscriptionsCascContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = globalSubscriptionsCascContext.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "items": {
                    "title": "SubscriptionsCascContextData",
                    "properties": {
                      "channel": {
                        "description": "Channel to send notifications to",
                        "type": "string"
                      },
                      "channelConfig": {
                        "description": "Configuration of the channel",
                        "type": {}
                      },
                      "contentTemplate": {
                        "description": "Optional template to use for the message",
                        "type": "string"
                      },
                      "disabled": {
                        "description": "Is this channel disabled?",
                        "type": "boolean"
                      },
                      "events": {
                        "items": {
                          "description": "List of events to listen to",
                          "type": "string"
                        },
                        "description": "List of events to listen to",
                        "type": "array"
                      },
                      "keywords": {
                        "description": "Keywords to filter the events",
                        "type": "string"
                      },
                      "name": {
                        "description": "Unique name of the subscription in its scope",
                        "type": "string"
                      }
                    },
                    "required": [
                      "channel",
                      "channelConfig",
                      "events",
                      "name"
                    ],
                    "additionalProperties": false,
                    "type": "object"
                  },
                  "description": "List of global subscriptions",
                  "type": "array"
                }
            """.trimIndent().parseAsJson(),
            type.asJson()
        )
    }

    @Test
    fun `A global subscription without a name is rejected`() {
        val target = uid("t")
        assertFailsWith<IllegalStateException> {
            casc(
                """
                ontrack:
                    extensions:
                        notifications:
                            global-subscriptions:
                                - events:
                                    - new_promotion_run
                                  channel: mock
                                  channel-config:
                                    target: "#$target"
                """.trimIndent()
            )
        }
        asAdmin {
            val subscriptions = eventSubscriptionService.filterSubscriptions(
                EventSubscriptionFilter(
                    channel = "mock",
                    channelConfig = "#$target"
                )
            )
            assertEquals(0, subscriptions.pageItems.size, "No subscription has been created")
        }
    }

    @Test
    fun `Creates a global subscription as code`() {
        val target = uid("t")
        val name = uid("g")
        casc("""
            ontrack:
                extensions:
                    notifications:
                        global-subscriptions:
                            - name: $name
                              events:
                                - new_promotion_run
                              keywords: "GOLD main"
                              channel: mock
                              channel-config:
                                target: "#$target"
        """.trimIndent())
        // Check we can find this global subscriptions
        asAdmin {
            val subscriptions = eventSubscriptionService.filterSubscriptions(
                EventSubscriptionFilter(
                    channel = "mock",
                    channelConfig = "#$target"
                )
            )
            assertEquals(1, subscriptions.pageItems.size)
            val subscription = subscriptions.pageItems.first()
            assertEquals(
                name,
                subscription.name
            )
            assertEquals(
                setOf("new_promotion_run"),
                subscription.events
            )
            assertEquals(
                "GOLD main",
                subscription.keywords
            )
            assertEquals(
                "mock",
                subscription.channel
            )
            assertEquals(
                mapOf("target" to "#$target").asJson(),
                subscription.channelConfig
            )
            assertEquals(
                false,
                subscription.disabled
            )
        }
    }

    @Test
    fun `Creates a global subscription as code with a content template`() {
        val target = uid("t")
        val name = uid("g")
        casc("""
            ontrack:
                extensions:
                    notifications:
                        global-subscriptions:
                            - name: $name
                              events:
                                - new_promotion_run
                              keywords: "GOLD main"
                              channel: mock
                              channel-config:
                                target: "#$target"
                              contentTemplate: |
                                This is a fairly simple template
                                for a ${'$'}{branch} name and
                                a ${'$'}{changelog?format=html}.
        """.trimIndent())
        // Check we can find this global subscriptions
        asAdmin {
            val subscriptions = eventSubscriptionService.filterSubscriptions(
                EventSubscriptionFilter(
                    channel = "mock",
                    channelConfig = "#$target"
                )
            )
            assertEquals(1, subscriptions.pageItems.size)
            val subscription = subscriptions.pageItems.first()
            assertEquals(
                setOf("new_promotion_run"),
                subscription.events
            )
            assertEquals(
                "GOLD main",
                subscription.keywords
            )
            assertEquals(
                "mock",
                subscription.channel
            )
            assertEquals(
                mapOf("target" to "#$target").asJson(),
                subscription.channelConfig
            )
            assertEquals(
                false,
                subscription.disabled
            )
            assertEquals(
                """
                    This is a fairly simple template
                    for a ${'$'}{branch} name and
                    a ${'$'}{changelog?format=html}.
                """.trimIndent(),
                subscription.contentTemplate
            )
        }
    }

}