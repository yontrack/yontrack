package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.extension.casc.CascService
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonObjectType
import net.nemerosa.ontrack.model.json.schema.JsonStringType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

@Disabled("FLAKY")
class EntitySubscriptionsCascContextIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var cascService: CascService

    @Autowired
    private lateinit var entitySubscriptionsCascContext: EntitySubscriptionsCascContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = entitySubscriptionsCascContext.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "items": {
                    "title": "EntitySubscriptionCascContextData",
                    "properties": {
                      "entity": {
                        "title": "EntitySubscriptionData",
                        "description": "Entity to subscribe to",
                        "properties": {
                          "branch": {
                            "description": "Branch name",
                            "type": "string"
                          },
                          "project": {
                            "description": "Project name",
                            "type": "string"
                          },
                          "promotion": {
                            "description": "Promotion level name",
                            "type": "string"
                          },
                          "validation": {
                            "description": "Validation stamp name",
                            "type": "string"
                          }
                        },
                        "required": [
                          "project"
                        ],
                        "additionalProperties": false,
                        "type": "object"
                      },
                      "subscriptions": {
                        "items": {
                          "title": "SubscriptionsCascContextData",
                          "description": "List of subscriptions for this entity",
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
                              "description": "Name of the subscription. Omitting it is deprecated: it is required in V6.",
                              "type": "string"
                            }
                          },
                          "required": [
                            "channel",
                            "channelConfig",
                            "events"
                          ],
                          "additionalProperties": false,
                          "type": "object"
                        },
                        "description": "List of subscriptions for this entity",
                        "type": "array"
                      }
                    },
                    "required": [
                      "entity",
                      "subscriptions"
                    ],
                    "additionalProperties": false,
                    "type": "object"
                  },
                  "description": "List of entity-level subscriptions",
                  "type": "array"
                }
            """.trimIndent().parseAsJson(),
            type.asJson()
        )
    }

    @Test
    fun `Subscription for a project`() {
        val target = uid("t")
        project {
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: ""
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                """
            )
            // Checks that we can find this subscription
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                )
                assertEquals(1, subscriptions.pageItems.size)
            }
            // Checking the subscription
            branch {
                val pl = promotionLevel()
                build {
                    // Creating the event
                    promote(pl)
                    // Checking the notification
                    assertNotNull(mockNotificationChannel.messages[target], "Received notification on promotion") {
                        assertEquals(
                            "Build $name has been promoted to ${pl.name} for branch ${branch.name} in ${project.name}.",
                            it.first()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Bad subscription format`() {
        val target = uid("t")
        project {
            assertFailsWith<IllegalStateException> {
                casc(
                    """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                        build: 1
                                      subscriptions:
                                        - events:
                                            - new_promotion_run
                                          keywords: ""
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                """
                )
            }
        }
    }

    @Test
    fun `Promotions and validations are not valid`() {
        val target = uid("t")
        project {
            branch {
                assertFailsWith<IllegalStateException> {
                    casc(
                        """
                            ontrack:
                                extensions:
                                    notifications:
                                        entity-subscriptions:
                                            - entity:
                                                project: ${project.name}
                                                branch: $name
                                                promotion: PL
                                                validation: VS
                                              subscriptions:
                                                - name: test
                                                  events:
                                                    - new_promotion_run
                                                  keywords: ""
                                                  channel: mock
                                                  channel-config:
                                                    target: "$target"
                        """
                    )
                }
            }
        }
    }

    @Test
    fun `Promotions need a branch`() {
        val target = uid("t")
        project {
            branch {
                assertFailsWith<IllegalStateException> {
                    casc(
                        """
                            ontrack:
                                extensions:
                                    notifications:
                                        entity-subscriptions:
                                            - entity:
                                                project: ${project.name}
                                                promotion: PL
                                              subscriptions:
                                                - name: test
                                                  events:
                                                    - new_promotion_run
                                                  keywords: ""
                                                  channel: mock
                                                  channel-config:
                                                    target: "$target"
                        """
                    )
                }
            }
        }
    }

    @Test
    fun `Validations need a branch`() {
        val target = uid("t")
        project {
            branch {
                assertFailsWith<IllegalStateException> {
                    casc(
                        """
                            ontrack:
                                extensions:
                                    notifications:
                                        entity-subscriptions:
                                            - entity:
                                                project: ${project.name}
                                                validation: VS
                                              subscriptions:
                                                - name: test
                                                  events:
                                                    - new_promotion_run
                                                  keywords: ""
                                                  channel: mock
                                                  channel-config:
                                                    target: "$target"
                        """
                    )
                }
            }
        }
    }

    @Test
    fun `Generated names for backward compatibility`() {
        val target = uid("t")
        project {
            val cascYaml = """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: "SILVER"
                                          channel: mock
                                          channel-config:
                                            target: "$target-silver"
                                        - events:
                                            - new_promotion_run
                                          keywords: "GOLD"
                                          channel: mock
                                          channel-config:
                                            target: "$target-gold"
                """
            // First generation
            casc(
                cascYaml
            )
            // Computing the actual name of the unnamed subscription
            val generatedName = EventSubscription.computeName(
                events = listOf("new_promotion_run"),
                keywords = "GOLD",
                channel = "mock",
                channelConfig = mapOf("target" to "$target-gold").asJson(),
                contentTemplate = null,
            )

            // Rendering
            fun checkRendering() {
                val json = entitySubscriptionsCascContext.render()
                assertEquals(
                    listOf(
                        mapOf(
                            "entity" to mapOf(
                                "project" to project.name,
                                "branch" to null,
                                "promotion" to null,
                                "validation" to null,
                            ),
                            "subscriptions" to listOf(
                                mapOf(
                                    "name" to "test",
                                    "events" to listOf("new_promotion_run"),
                                    "keywords" to "SILVER",
                                    "channel" to "mock",
                                    "channelConfig" to mapOf(
                                        "target" to "$target-silver"
                                    ),
                                    "disabled" to false,
                                    "contentTemplate" to null,
                                ),
                                mapOf(
                                    "name" to generatedName,
                                    "events" to listOf("new_promotion_run"),
                                    "keywords" to "GOLD",
                                    "channel" to "mock",
                                    "channelConfig" to mapOf(
                                        "target" to "$target-gold"
                                    ),
                                    "disabled" to false,
                                    "contentTemplate" to null,
                                ),
                            )
                        )
                    ).asJson(),
                    json
                )
            }
            checkRendering()
            // Second generation, still unnamed
            casc(
                cascYaml
            )
            checkRendering()
        }
    }

    @Test
    fun `Rendering with a template`() {
        val target = uid("t")
        project {
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: "SILVER"
                                          channel: mock
                                          channel-config:
                                            target: "$target-silver"
                                          contentTemplate: |
                                            This is my template.
                """.trimIndent()
            )
            // Rendering
            val json = entitySubscriptionsCascContext.render()
            assertEquals(
                listOf(
                    mapOf(
                        "entity" to mapOf(
                            "project" to name,
                            "branch" to null,
                            "promotion" to null,
                            "validation" to null,
                        ),
                        "subscriptions" to listOf(
                            mapOf(
                                "name" to "test",
                                "events" to listOf("new_promotion_run"),
                                "keywords" to "SILVER",
                                "channel" to "mock",
                                "channelConfig" to mapOf(
                                    "target" to "$target-silver"
                                ),
                                "disabled" to false,
                                "contentTemplate" to "This is my template.",
                            ),
                        )
                    )
                ).asJson(),
                json
            )
        }
    }

    @Test
    fun `Updating the subscription for a project`() {
        val target = uid("t")
        project {
            // Initial subscriptions
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test-1
                                          events:
                                            - new_promotion_run
                                          keywords: "GOLD"
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                                        - name: test-2
                                          events:
                                            - new_promotion_run
                                          keywords: "SILVER"
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                """
            )
            // Checks that we can find these subscriptions
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                ).pageItems.map { it.keywords }
                assertEquals(
                    setOf("GOLD", "SILVER"),
                    subscriptions.toSet()
                )
            }
            // New subscriptions
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test-1
                                          events:
                                            - new_promotion_run
                                          keywords: "PLATINUM"
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                                        - name: test-2
                                          events:
                                            - new_promotion_run
                                          keywords: "SILVER"
                                          channel: mock
                                          channel-config:
                                            target: "$target-silver"
                """
            )
            // Checks that we can find this subscription
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                ).pageItems.map { it.keywords }
                assertEquals(
                    setOf("PLATINUM", "SILVER"),
                    subscriptions.toSet()
                )
            }
        }
    }

    @Test
    fun `Deleting an entity from the list of subscriptions`() {
        val target = uid("t")
        project {
            // Initial subscriptions
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test-1
                                          events:
                                            - new_promotion_run
                                          keywords: "GOLD"
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                                        - name: test-2
                                          events:
                                            - new_promotion_run
                                          keywords: "SILVER"
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                """
            )
            // Checks that we can find these subscriptions
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                ).pageItems.map { it.keywords }
                assertEquals(
                    setOf("GOLD", "SILVER"),
                    subscriptions.toSet()
                )
            }
            // Removing the entity-level
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions: []
                """
            )
            // Checks that we cannot find these subscriptions any longer
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                ).pageItems
                assertTrue(
                    subscriptions.isEmpty(),
                    "Subscriptions are gone"
                )
            }
        }
    }

    @Test
    fun `Subscription for a branch`() {
        val target = uid("t")
        project {
            branch {
                casc(
                    """
                        ontrack:
                            extensions:
                                notifications:
                                    entity-subscriptions:
                                        - entity:
                                            project: ${project.name}
                                            branch: $name
                                          subscriptions:
                                            - name: test-1
                                              events:
                                                - new_promotion_run
                                              keywords: ""
                                              channel: mock
                                              channel-config:
                                                target: "$target"
                    """
                )
                // Checks that we can find this subscription
                asAdmin {
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = this.toProjectEntityID(),
                            origin = "casc",
                        )
                    )
                    assertEquals(1, subscriptions.pageItems.size)
                }
                // Checking the subscription
                val pl = promotionLevel()
                build {
                    // Creating the event
                    promote(pl)
                    // Checking the notification
                    assertNotNull(mockNotificationChannel.messages[target], "Received notification on promotion") {
                        assertEquals(
                            "Build $name has been promoted to ${pl.name} for branch ${branch.name} in ${project.name}.",
                            it.first()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Subscription for a validation`() {
        val target = uid("t")
        project {
            branch {
                val vs = validationStamp()
                casc(
                    """
                        ontrack:
                            extensions:
                                notifications:
                                    entity-subscriptions:
                                        - entity:
                                            project: ${project.name}
                                            branch: $name
                                            validation: ${vs.name}
                                          subscriptions:
                                            - name: test
                                              events:
                                                - new_validation_run
                                              keywords: ""
                                              channel: mock
                                              channel-config:
                                                target: "$target"
                    """
                )
                // Checks that we can find this subscription
                asAdmin {
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = vs.toProjectEntityID(),
                            origin = "casc",
                        )
                    )
                    assertEquals(1, subscriptions.pageItems.size)
                }
                // Checking the subscription
                build {
                    // Creating the event
                    validate(vs)
                    // Checking the notification
                    assertNotNull(mockNotificationChannel.messages[target], "Received notification on promotion") {
                        assertEquals(
                            "Build $name has run for the ${vs.name} with status Passed in branch ${branch.name} in ${project.name}.",
                            it.first()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Subscription for a failed validation`() {
        val target = uid("t")
        project {
            branch {
                val vs = validationStamp()
                casc(
                    """
                        ontrack:
                            extensions:
                                notifications:
                                    entity-subscriptions:
                                        - entity:
                                            project: ${project.name}
                                            branch: $name
                                            validation: ${vs.name}
                                          subscriptions:
                                            - name: test
                                              events:
                                                - new_validation_run
                                              keywords: "failed"
                                              channel: mock
                                              channel-config:
                                                target: "$target"
                    """
                )
                // Checks that we can find this subscription
                asAdmin {
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = vs.toProjectEntityID(),
                            origin = "casc",
                        )
                    )
                    assertEquals(1, subscriptions.pageItems.size)
                }
                // Checking the subscription
                build {
                    // Creating the event for a successful validation
                    validate(vs)
                    // Checking that there is no notification
                    assertNull(
                        mockNotificationChannel.messages[target],
                        "Received no notification on passed validation"
                    )
                    // Creating the event for a failed validation
                    validate(vs, ValidationRunStatusID.STATUS_FAILED)
                    // Checking the notification
                    assertNotNull(
                        mockNotificationChannel.messages[target],
                        "Received notification on failed notification"
                    ) {
                        assertEquals(
                            "Build $name has run for the ${vs.name} with status Failed in branch ${branch.name} in ${project.name}.",
                            it.first()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Subscription for a non-existing project`() {
        val target = uid("t")
        val projectName = uid("p")
        casc(
            """
                ontrack:
                    extensions:
                        notifications:
                            entity-subscriptions:
                                - entity:
                                    project: $projectName
                                  subscriptions:
                                    - name: test
                                      events:
                                        - new_promotion_run
                                      keywords: ""
                                      channel: mock
                                      channel-config:
                                        target: "$target"
            """
        )
        // Creating the project now
        project(NameDescription.nd(projectName, "")) {
            // Checks that we cannot find any subscription
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                )
                assertEquals(0, subscriptions.pageItems.size)
            }
            // Running the Casc again
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: ""
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                """
            )
            // Checks that we can find this subscription
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                )
                assertEquals(1, subscriptions.pageItems.size)
            }
            // Checking the subscription
            branch {
                val pl = promotionLevel()
                build {
                    // Creating the event
                    promote(pl)
                    // Checking the notification
                    assertNotNull(mockNotificationChannel.messages[target], "Received notification on promotion") {
                        assertEquals(
                            "Build $name has been promoted to ${pl.name} for branch ${branch.name} in ${project.name}.",
                            it.first()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Subscription for a promotion`() {
        val target = uid("t")
        project {
            branch {
                val pl = promotionLevel()
                casc(
                    """
                        ontrack:
                            extensions:
                                notifications:
                                    entity-subscriptions:
                                        - entity:
                                            project: ${pl.project.name}
                                            branch: ${pl.branch.name}
                                            promotion: ${pl.name}
                                          subscriptions:
                                            - name: test
                                              events:
                                                - new_promotion_run
                                              keywords: ""
                                              channel: mock
                                              channel-config:
                                                target: "$target"
                    """
                )
                // Checks that we can find this subscription
                asAdmin {
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = pl.toProjectEntityID(),
                            origin = "casc",
                        )
                    )
                    assertEquals(1, subscriptions.pageItems.size)
                    val subscription = subscriptions.pageItems.first()
                    assertEquals(
                        setOf("new_promotion_run"),
                        subscription.events
                    )
                    assertEquals(
                        "",
                        subscription.keywords
                    )
                    assertEquals(
                        "mock",
                        subscription.channel
                    )
                    assertEquals(
                        mapOf("target" to target).asJson(),
                        subscription.channelConfig
                    )
                    assertEquals(
                        false,
                        subscription.disabled
                    )
                }
                // Checking the subscription
                build {
                    // Creating the event
                    promote(pl)
                    // Checking the notification
                    assertNotNull(mockNotificationChannel.messages[target], "Received notification on promotion") {
                        assertEquals(
                            "Build $name has been promoted to ${pl.name} for branch ${branch.name} in ${project.name}.",
                            it.first()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Subscription for a promotion with some content template`() {
        val target = uid("t")
        project {
            branch {
                val pl = promotionLevel()
                casc(
                    """
                        ontrack:
                            extensions:
                                notifications:
                                    entity-subscriptions:
                                        - entity:
                                            project: ${pl.project.name}
                                            branch: ${pl.branch.name}
                                            promotion: ${pl.name}
                                          subscriptions:
                                            - name: test
                                              events:
                                                - new_promotion_run
                                              keywords: ""
                                              channel: mock
                                              channel-config:
                                                target: "$target"
                                              contentTemplate: |
                                                Change log for ${'$'}{build.release}
                                                
                                                ${'$'}{changelog?format=text}
                                                
                                                ${'$'}{changelog?format=text&project=dep-01}
                    """.trimIndent()
                )
                // Checks that we can find this subscription
                asAdmin {
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = pl.toProjectEntityID(),
                            origin = "casc",
                        )
                    )
                    assertEquals(1, subscriptions.pageItems.size)
                    val subscription = subscriptions.pageItems.first()
                    assertEquals(
                        setOf("new_promotion_run"),
                        subscription.events
                    )
                    assertEquals(
                        "",
                        subscription.keywords
                    )
                    assertEquals(
                        "mock",
                        subscription.channel
                    )
                    assertEquals(
                        mapOf("target" to target).asJson(),
                        subscription.channelConfig
                    )
                    assertEquals(
                        false,
                        subscription.disabled
                    )
                    assertEquals(
                        """
                            Change log for ${'$'}{build.release}
                            
                            ${'$'}{changelog?format=text}
                            
                            ${'$'}{changelog?format=text&project=dep-01}
                        """.trimIndent(),
                        subscription.contentTemplate
                    )
                }
            }
        }
    }

    @Test
    fun `Duplicated entity`() {
        val target = uid("t")
        project {
            val ex = assertFails {
                casc(
                    """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: ""
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: "GOLD"
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                    """
                )
            }
            assertEquals(
                """
                Duplicate entities in the notifications:
                 * $name
            """.trimIndent(), ex.message
            )
        }
    }

    @Test
    fun `Subscription for a project changing only the channel configuration`() {
        val target = uid("t")
        val newTarget = uid("nt")
        project {
            // Initial subscription
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: ""
                                          channel: mock
                                          channel-config:
                                            target: "$target"
                """
            )
            // Checks that we can find this subscription
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                ).pageItems.map { it.channelConfig.getRequiredTextField("target") }
                assertEquals(listOf(target), subscriptions)
            }
            // Changing only the target channel
            casc(
                """
                    ontrack:
                        extensions:
                            notifications:
                                entity-subscriptions:
                                    - entity:
                                        project: $name
                                      subscriptions:
                                        - name: test
                                          events:
                                            - new_promotion_run
                                          keywords: ""
                                          channel: mock
                                          channel-config:
                                            target: "$newTarget"
                """
            )
            // Checks that we can find this subscription and only this one
            asAdmin {
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = this.toProjectEntityID(),
                        origin = "casc",
                    )
                ).pageItems.map { it.channelConfig.getRequiredTextField("target") }
                assertEquals(listOf(newTarget), subscriptions)
            }
        }
    }

    @Test
    fun `Ignoring the storage key when generating the Casc model for EntitySubscriptionData`() {
        val type = entitySubscriptionsCascContext.jsonType(jsonTypeBuilder)
        val list = assertIs<JsonArrayType>(type)
        val itemProperties = (list.items as JsonObjectType).properties
        val entityProperties = (itemProperties["entity"] as JsonObjectType).properties
        assertIs<JsonStringType>(entityProperties["project"])
        assertNull(entityProperties["storageKey"])
    }

    /**
     * Runs a CasC from a series of YAML texts
     */
    private fun casc(vararg yaml: String) {
        asAdmin {
            cascService.runYaml(*yaml)
        }
    }

}