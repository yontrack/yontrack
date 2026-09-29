package net.nemerosa.ontrack.extension.notifications.subscriptions

import graphql.ErrorType
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredBooleanField
import net.nemerosa.ontrack.json.getRequiredJsonField
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.*

@AsAdminTest
class EventSubscriptionMutationsIT : AbstractNotificationTestSupport() {

    @Test
    fun `A subscription without a name is rejected by the entity mutation`() {
        asAdmin {
            project {
                branch {
                    runWithError(
                        """
                        mutation {
                            subscribeBranchToEvents(input: {
                                project: "${project.name}",
                                branch: "$name",
                                channel: "mock",
                                channelConfig: {
                                    target: "#test"
                                },
                                events: [
                                    "new_promotion_run"
                                ],
                            }) {
                                errors {
                                    message
                                }
                            }
                        }
                    """,
                        errorClassification = ErrorType.ValidationError,
                    )
                    assertTrue(
                        eventSubscriptionService.filterSubscriptions(
                            EventSubscriptionFilter(entity = toProjectEntityID())
                        ).pageItems.isEmpty(),
                        "No subscription has been created"
                    )
                }
            }
        }
    }

    @Test
    fun `A subscription without a name is rejected by the generic mutation`() {
        asAdmin {
            project {
                runWithError(
                    """
                    mutation {
                        subscribeToEvents(input: {
                            projectEntity: {
                                type: PROJECT,
                                id: $id
                            },
                            channel: "mock",
                            channelConfig: {
                                target: "#test"
                            },
                            events: [
                                "new_promotion_run"
                            ],
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """,
                    errorClassification = ErrorType.ValidationError,
                )
                assertTrue(
                    eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(entity = toProjectEntityID())
                    ).pageItems.isEmpty(),
                    "No subscription has been created"
                )
            }
        }
    }

    @Test
    fun `Renaming a subscription for an entity`() {
        asAdmin {
            project {
                eventSubscriptionService.subscribe(
                    EventSubscription(
                        projectEntity = this,
                        name = "Old name",
                        events = setOf(EventFactory.NEW_BRANCH.id),
                        keywords = null,
                        channel = "mock",
                        channelConfig = mapOf("target" to "group").asJson(),
                        disabled = false,
                        origin = "test",
                        contentTemplate = null,
                    )
                )
                // Finding this initial subscription by name
                assertNotNull(eventSubscriptionService.findSubscriptionByName(this, "Old name"))
                // Renaming the subscription
                run("""
                    mutation {
                        renameSubscription(input: {
                            projectEntity: {
                                type: PROJECT,
                                id: $id
                            },
                            name: "Old name",
                            newName: "New name",
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """) { data ->
                    checkGraphQLUserErrors(data, "renameSubscription")
                }
                // Initial subscription name is not found
                assertNull(eventSubscriptionService.findSubscriptionByName(this, "Old name"))
                // New name is found
                assertNotNull(eventSubscriptionService.findSubscriptionByName(this, "New name"))
            }
        }
    }

    @Test
    fun `Settings subscriptions using the branch mutation`() {
        asAdmin {
            project {
                branch {
                    run(
                        """
                        mutation {
                            subscribeBranchToEvents(input: {
                                project: "${project.name}",
                                branch: "$name",
                                name: "test",
                                channel: "mock",
                                channelConfig: {
                                    target: "#test"
                                },
                                events: [
                                    "new_promotion_run"
                                ],
                                keywords: "GOLD",
                            }) {
                                errors {
                                    message
                                }
                                subscription {
                                    name
                                }
                            }
                        }
                    """
                    ) { data ->
                        checkGraphQLUserErrors(data, "subscribeBranchToEvents") { payload ->
                            val name = payload.getRequiredJsonField("subscription").getRequiredTextField("name")
                            assertEquals("test", name)
                            val subscription: EventSubscription =
                                eventSubscriptionService.getSubscriptionByName(this, name)
                            assertEquals(
                                setOf(
                                    "new_promotion_run"
                                ),
                                subscription.events
                            )
                            assertEquals(this, subscription.projectEntity)
                            assertEquals("GOLD", subscription.keywords)
                            assertEquals(
                                "mock",
                                subscription.channel
                            )
                            assertEquals(
                                mapOf("target" to "#test").asJson(),
                                subscription.channelConfig
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Settings subscriptions using the promotion level mutation`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    run(
                        """
                        mutation {
                            subscribePromotionLevelToEvents(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "GOLD",
                                name: "test",
                                channel: "mock",
                                channelConfig: {
                                    target: "#test"
                                },
                                events: [
                                    "new_promotion_run"
                                ]
                            }) {
                                errors {
                                    message
                                }
                                subscription {
                                    name
                                }
                            }
                        }
                    """
                    ) { data ->
                        checkGraphQLUserErrors(data, "subscribePromotionLevelToEvents") { payload ->
                            val name = payload.getRequiredJsonField("subscription").getRequiredTextField("name")
                            val subscription: EventSubscription =
                                eventSubscriptionService.getSubscriptionByName(pl, name)
                            assertEquals(
                                setOf(
                                    "new_promotion_run"
                                ),
                                subscription.events
                            )
                            assertEquals(pl, subscription.projectEntity)
                            assertEquals(null, subscription.keywords)
                            assertEquals(
                                "mock",
                                subscription.channel
                            )
                            assertEquals(
                                mapOf("target" to "#test").asJson(),
                                subscription.channelConfig
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Settings subscriptions using the promotion level mutation with a custom content template`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    run(
                        """
                        mutation SetupPromotionSubscription(
                            ${'$'}contentTemplate: String!,
                        ) {
                            subscribePromotionLevelToEvents(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "GOLD",
                                name: "test",
                                channel: "mock",
                                channelConfig: {
                                    target: "#test"
                                },
                                events: [
                                    "new_promotion_run"
                                ],
                                contentTemplate: ${'$'}contentTemplate,
                            }) {
                                errors {
                                    message
                                }
                                subscription {
                                    name
                                }
                            }
                        }
                    """,
                        variables = mapOf(
                            "contentTemplate" to "Change log for this promotion!"
                        )
                    ) { data ->
                        checkGraphQLUserErrors(data, "subscribePromotionLevelToEvents") { payload ->
                            val name = payload.getRequiredJsonField("subscription").getRequiredTextField("name")
                            val subscription: EventSubscription =
                                eventSubscriptionService.getSubscriptionByName(pl, name)
                            assertEquals(
                                setOf(
                                    "new_promotion_run"
                                ),
                                subscription.events
                            )
                            assertEquals(pl, subscription.projectEntity)
                            assertEquals(null, subscription.keywords)
                            assertEquals(
                                "mock",
                                subscription.channel
                            )
                            assertEquals(
                                mapOf("target" to "#test").asJson(),
                                subscription.channelConfig
                            )
                            assertEquals(
                                "Change log for this promotion!",
                                subscription.contentTemplate,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Settings subscriptions using the promotion level mutation with a custom content template and updating it`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    // Creating the initial subscription
                    run(
                        """
                        mutation SetupPromotionSubscription(
                            ${'$'}contentTemplate: String!,
                        ) {
                            subscribePromotionLevelToEvents(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "GOLD",
                                name: "test",
                                channel: "mock",
                                channelConfig: {
                                    target: "#test"
                                },
                                events: [
                                    "new_promotion_run"
                                ],
                                contentTemplate: ${'$'}contentTemplate,
                            }) {
                                errors {
                                    message
                                }
                                subscription {
                                    name
                                }
                            }
                        }
                    """,
                        variables = mapOf(
                            "contentTemplate" to "Change log for this promotion!"
                        )
                    ) {}
                    // Updating the content template
                    run(
                        """
                        mutation SetupPromotionSubscription(
                            ${'$'}contentTemplate: String!,
                        ) {
                            subscribePromotionLevelToEvents(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "GOLD",
                                name: "test",
                                channel: "mock",
                                channelConfig: {
                                    target: "#test"
                                },
                                events: [
                                    "new_promotion_run"
                                ],
                                contentTemplate: ${'$'}contentTemplate,
                            }) {
                                errors {
                                    message
                                }
                                subscription {
                                    name
                                }
                            }
                        }
                    """,
                        variables = mapOf(
                            "contentTemplate" to "Change log for this promotion with a link ${'$'}{promotionLevel}!"
                        )
                    ) {}
                    // Checking that only the last subscription has been taken into account
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(entity = pl.toProjectEntityID())
                    ).pageItems
                    assertEquals(1, subscriptions.size, "Only one subscription must be kept")
                    val subscription = subscriptions.first()
                    assertEquals(
                        "Change log for this promotion with a link ${'$'}{promotionLevel}!",
                        subscription.contentTemplate
                    )
                }
            }
        }
    }

    @Test
    fun `Disabling and enabling a subscription for an entity`() {
        asAdmin {
            project {
                eventSubscriptionService.subscribe(
                    name = "test",
                    channel = mockNotificationChannel,
                    channelConfig = MockNotificationChannelConfig("#target"),
                    projectEntity = this,
                    keywords = null,
                    origin = "test",
                    contentTemplate = null,
                    EventFactory.NEW_PROMOTION_RUN,
                )
                // Disabling
                run(
                    """
                    mutation {
                        disableSubscription(input: {
                            projectEntity: {
                                type: PROJECT,
                                id: $id
                            },
                            name: "test"
                        }) {
                            errors {
                                message
                            }
                            subscription {
                                name
                                disabled
                            }
                        }
                    }
                """
                ) { data ->
                    checkGraphQLUserErrors(data, "disableSubscription") { payload ->
                        assertEquals("test", payload.path("subscription").getRequiredTextField("name"))
                        assertEquals(true, payload.path("subscription").getRequiredBooleanField("disabled"))
                        assertNotNull(eventSubscriptionService.findSubscriptionByName(this, "test")) {
                            assertTrue(it.disabled, "Subscription is disabled")
                        }
                    }
                }
                // Enabling
                run(
                    """
                    mutation {
                        enableSubscription(input: {
                            projectEntity: {
                                type: PROJECT,
                                id: $id
                            },
                            name: "test"
                        }) {
                            errors {
                                message
                            }
                            subscription {
                                name
                                disabled
                            }
                        }
                    }
                """
                ) { data ->
                    checkGraphQLUserErrors(data, "enableSubscription") { payload ->
                        assertEquals("test", payload.path("subscription").getRequiredTextField("name"))
                        assertEquals(false, payload.path("subscription").getRequiredBooleanField("disabled"))
                        assertNotNull(eventSubscriptionService.findSubscriptionByName(this, "test")) {
                            assertFalse(it.disabled, "Subscription is enabled")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Disabling and enabling a global subscription`() {
        asAdmin {
            val name = uid("g-")
            eventSubscriptionService.removeAllGlobal()
            eventSubscriptionService.subscribe(
                name = name,
                channel = mockNotificationChannel,
                channelConfig = MockNotificationChannelConfig("#target"),
                projectEntity = null,
                keywords = null,
                origin = "test",
                contentTemplate = null,
                EventFactory.NEW_PROMOTION_RUN,
            )
            // Disabling
            run(
                """
                    mutation {
                        disableSubscription(input: {
                            name: "$name"
                        }) {
                            errors {
                                message
                            }
                            subscription {
                                name
                                disabled
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "disableSubscription") { payload ->
                    assertEquals(name, payload.path("subscription").getRequiredTextField("name"))
                    assertEquals(true, payload.path("subscription").getRequiredBooleanField("disabled"))
                    assertNotNull(eventSubscriptionService.findSubscriptionByName(null, name)) {
                        assertTrue(it.disabled, "Subscription is disabled")
                    }
                }
            }
            // Enabling
            run(
                """
                    mutation {
                        enableSubscription(input: {
                            name: "$name"
                        }) {
                            errors {
                                message
                            }
                            subscription {
                                name
                                disabled
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "enableSubscription") { payload ->
                    assertEquals(name, payload.path("subscription").getRequiredTextField("name"))
                    assertEquals(false, payload.path("subscription").getRequiredBooleanField("disabled"))
                    assertNotNull(eventSubscriptionService.findSubscriptionByName(null, name)) {
                        assertFalse(it.disabled, "Subscription is enabled")
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a global subscription without a template`() {
        project {
            run(
                """
                mutation {
                    subscribeToEvents(input: {
                        projectEntity: {
                            type: PROJECT,
                            id: $id
                        },
                        name: "test",
                        channel: "mock",
                        channelConfig: {
                            target: "#test"
                        },
                        events: [
                            "new_branch",
                            "delete_branch",
                            "new_promotion_run"
                        ],
                    }) {
                        errors {
                            message
                        }
                        subscription {
                            name
                        }
                    }
                }
            """
            ) { data ->
                checkGraphQLUserErrors(data, "subscribeToEvents") { payload ->
                    val name = payload.getRequiredJsonField("subscription").getRequiredTextField("name")
                    val subscription: EventSubscription = eventSubscriptionService.getSubscriptionByName(this, name)
                    assertEquals(
                        setOf(
                            "new_branch",
                            "delete_branch",
                            "new_promotion_run"
                        ),
                        subscription.events
                    )
                    assertEquals(this, subscription.projectEntity)
                    assertEquals(
                        "mock",
                        subscription.channel
                    )
                    assertEquals(
                        mapOf("target" to "#test").asJson(),
                        subscription.channelConfig
                    )
                    assertEquals(
                        null,
                        subscription.contentTemplate,
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a global subscription with a template`() {
        project {
            run(
                """
                mutation {
                    subscribeToEvents(input: {
                        projectEntity: {
                            type: PROJECT,
                            id: $id
                        },
                        name: "test",
                        channel: "mock",
                        channelConfig: {
                            target: "#test"
                        },
                        events: [
                            "new_branch",
                            "delete_branch",
                            "new_promotion_run"
                        ],
                        contentTemplate: "This is a poor template",
                    }) {
                        errors {
                            message
                        }
                        subscription {
                            name
                        }
                    }
                }
            """
            ) { data ->
                checkGraphQLUserErrors(data, "subscribeToEvents") { payload ->
                    val name = payload.getRequiredJsonField("subscription").getRequiredTextField("name")
                    val subscription: EventSubscription = eventSubscriptionService.getSubscriptionByName(this, name)
                    assertEquals(
                        setOf(
                            "new_branch",
                            "delete_branch",
                            "new_promotion_run"
                        ),
                        subscription.events
                    )
                    assertEquals(this, subscription.projectEntity)
                    assertEquals(
                        "mock",
                        subscription.channel
                    )
                    assertEquals(
                        mapOf("target" to "#test").asJson(),
                        subscription.channelConfig
                    )
                    assertEquals(
                        "This is a poor template",
                        subscription.contentTemplate,
                    )
                }
            }
        }
    }

    @Test
    fun `Deleting a global subscription`() {
        asAdmin {
            val name = uid("g-")
            eventSubscriptionService.subscribe(
                name = name,
                channel = mockNotificationChannel,
                channelConfig = MockNotificationChannelConfig("#main"),
                projectEntity = null,
                keywords = null,
                origin = "test",
                contentTemplate = null,
                EventFactory.NEW_PROMOTION_RUN,
            )
            run(
                """
                    mutation {
                        deleteSubscription(input:{id: "$name"}) {
                            errors {
                                message
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "deleteSubscription")
                assertNull(
                    eventSubscriptionService.findSubscriptionByName(null, name),
                    "Subscription has been deleted"
                )
            }
        }
    }

    @Test
    fun `Deleting an entity subscription`() {
        asAdmin {
            project {
                eventSubscriptionService.subscribe(
                    projectEntity = this,
                    name = "test",
                    channel = mockNotificationChannel,
                    channelConfig = MockNotificationChannelConfig("#main"),
                    keywords = null,
                    origin = "test",
                    contentTemplate = null,
                    eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                )
                run(
                    """
                    mutation {
                        deleteSubscription(input:{
                            id: "test",
                            projectEntity: {
                                type: PROJECT,
                                id: $id
                            }
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """
                ) { data ->
                    checkGraphQLUserErrors(data, "deleteSubscription")
                    assertNull(
                        eventSubscriptionService.findSubscriptionByName(this, "test"),
                        "Subscription has been deleted"
                    )
                }
            }
        }
    }

}