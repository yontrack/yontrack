package net.nemerosa.ontrack.extension.notifications.recording

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelOutput
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscription
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredIntField
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertJsonNotNull
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals

internal class GQLRootQueryNotificationRecordsIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var notificationRecordingService: NotificationRecordingService

    @Test
    fun `Getting the list of notifications`() {
        asAdmin {
            notificationRecordingService.clearAll()
            // One event
            val project = project {}
            val event = eventFactory.newProject(project)
            // Records a notification
            notificationRecordingService.record(
                NotificationRecord(
                    id = UUID.randomUUID().toString(),
                    source = mockSource(),
                    timestamp = Time.now(),
                    channel = "mock",
                    channelConfig = MockNotificationChannelConfig("#target").asJson(),
                    event = event.asJson(),
                    result = NotificationResult.ok(
                        output = MockNotificationChannelOutput(text = "The actual text", data = null)
                    ).toNotificationRecordResult(),
                )
            )
            // Getting this notification
            run(
                """{
                notificationRecords {
                    pageItems {
                        timestamp
                        source {
                            id
                            data
                        }
                        channel
                        channelConfig
                        event
                        result {
                            type
                            message
                        }
                    }
                }
            }"""
            ) { data ->
                assertJsonNotNull(data.path("notificationRecords").path("pageItems").path(0)) {
                    assertEquals("mock", getRequiredTextField("channel"))
                    assertEquals("#target", path("channelConfig").getRequiredTextField("target"))
                    assertEquals(project.id(), path("event").path("entities").path("PROJECT").getRequiredIntField("id"))
                    assertEquals("OK", path("result").getRequiredTextField("type"))
                    assertJsonNull(path("result").path("message"))
                    assertEquals("mock", path("source").path("id").asText())
                    assertEquals("test", path("source").path("data").path("text").asText())
                }
            }
        }
    }

    @Test
    fun `Getting a notification record by ID`() {
        asAdmin {
            // One event
            val project = project {}
            val event = eventFactory.newProject(project)
            // Records a notification
            val recordId = UUID.randomUUID().toString()
            notificationRecordingService.record(
                NotificationRecord(
                    id = recordId,
                    source = mockSource(),
                    timestamp = Time.now(),
                    channel = "mock",
                    channelConfig = MockNotificationChannelConfig("#target").asJson(),
                    event = event.asJson(),
                    result = NotificationResult.ok(
                        output = MockNotificationChannelOutput(text = "The actual text", data = null)
                    ).toNotificationRecordResult(),
                )
            )
            // Getting this notification record
            run(
                """{
                    notificationRecord(id: "$recordId") {
                        id
                        timestamp
                        source {
                            id
                            data
                        }
                        channel
                        channelConfig
                        event
                        result {
                            type
                            message
                        }
                    }
                }"""
            ) { data ->
                assertJsonNotNull(data.path("notificationRecord")) {
                    assertEquals(recordId, getRequiredTextField("id"))
                    assertEquals("mock", getRequiredTextField("channel"))
                    assertEquals("#target", path("channelConfig").getRequiredTextField("target"))
                    assertEquals(project.id(), path("event").path("entities").path("PROJECT").getRequiredIntField("id"))
                    assertEquals("OK", path("result").getRequiredTextField("type"))
                    assertJsonNull(path("result").path("message"))
                    assertEquals("mock", path("source").path("id").asText())
                    assertEquals("test", path("source").path("data").path("text").asText())
                }
            }
        }
    }

    @Test
    fun `Getting the list of notifications filtered by result type`() {
        asAdmin {
            notificationRecordingService.clearAll()
            // One event
            val project = project {}
            val event = eventFactory.newProject(project)
            // Records a OK notification
            notificationRecordingService.record(
                NotificationRecord(
                    id = UUID.randomUUID().toString(),
                    source = null,
                    timestamp = Time.now(),
                    channel = "mock",
                    channelConfig = MockNotificationChannelConfig("#target").asJson(),
                    event = event.asJson(),
                    result = NotificationResult.ok(
                        output = MockNotificationChannelOutput(text = "The actual text", data = null)
                    ).toNotificationRecordResult(),
                )
            )
            // Records a misconfigured notification
            notificationRecordingService.record(
                NotificationRecord(
                    id = UUID.randomUUID().toString(),
                    source = null,
                    timestamp = Time.now(),
                    channel = "mock",
                    channelConfig = MockNotificationChannelConfig("not-valid").asJson(),
                    event = event.asJson(),
                    result = NotificationResult.invalidConfiguration<MockNotificationChannelOutput>()
                        .toNotificationRecordResult()
                )
            )
            // Getting this notification
            run(
                """{
                notificationRecords(
                    resultType: INVALID_CONFIGURATION
                ) {
                    pageItems {
                        timestamp
                        channel
                        channelConfig
                        event
                        result {
                            type
                            message
                        }
                    }
                }
            }"""
            ) { data ->
                assertJsonNotNull(data.path("notificationRecords").path("pageItems").path(0)) {
                    assertEquals("mock", getRequiredTextField("channel"))
                    assertEquals("not-valid", path("channelConfig").getRequiredTextField("target"))
                    assertEquals(project.id(), path("event").path("entities").path("PROJECT").getRequiredIntField("id"))
                    assertEquals("INVALID_CONFIGURATION", path("result").getRequiredTextField("type"))
                    assertEquals("Invalid configuration", path("result").getTextField("message"))
                }
            }
        }
    }

    @Test
    fun `Getting the list of notifications filtered by entity subscription`() {
        asAdmin {
            notificationRecordingService.clearAll()
            project {
                branch {
                    val silver = promotionLevel()
                    eventSubscriptionService.subscribe(
                        EventSubscription(
                            projectEntity = silver,
                            name = "Mail",
                            events = setOf(EventFactory.NEW_PROMOTION_RUN.id),
                            keywords = null,
                            channel = "mock",
                            channelConfig = MockNotificationChannelConfig("mail-silver").asJson(),
                            disabled = false,
                            origin = "test",
                            contentTemplate = null,
                        )
                    )

                    val pl = promotionLevel()
                    eventSubscriptionService.subscribe(
                        EventSubscription(
                            projectEntity = pl,
                            name = "Mail",
                            events = setOf(EventFactory.NEW_PROMOTION_RUN.id),
                            keywords = null,
                            channel = "mock",
                            channelConfig = MockNotificationChannelConfig("mail").asJson(),
                            disabled = false,
                            origin = "test",
                            contentTemplate = null,
                        )
                    )
                    eventSubscriptionService.subscribe(
                        EventSubscription(
                            projectEntity = pl,
                            name = "Slack",
                            events = setOf(EventFactory.NEW_PROMOTION_RUN.id),
                            keywords = null,
                            channel = "mock",
                            channelConfig = MockNotificationChannelConfig("slack").asJson(),
                            disabled = false,
                            origin = "test",
                            contentTemplate = null,
                        )
                    )

                    build {
                        // Triggering three notifications
                        promote(pl)
                        promote(silver)

                        // Filtering on this entity
                        run(
                            """{
                                notificationRecords(
                                    sourceId: "entity-subscription",
                                    sourceData: {
                                        entityType: "PROMOTION_LEVEL",
                                        entityId: ${pl.id}
                                    }
                                ) {
                                    pageItems {
                                        channelConfig
                                    }
                                }
                            }"""
                        ) { data ->
                            val targets = data.path("notificationRecords").path("pageItems")
                                .values().map { record ->
                                    record.path("channelConfig").path("target").asText()
                                }
                            assertEquals(
                                setOf("mail", "slack"),
                                targets.toSet()
                            )
                        }

                        // Filtering on one exact subscription
                        run(
                            """{
                                notificationRecords(
                                    sourceId: "entity-subscription",
                                    sourceData: {
                                        entityType: "PROMOTION_LEVEL",
                                        entityId: ${pl.id},
                                        subscriptionName: "Mail"
                                    }
                                ) {
                                    pageItems {
                                        channelConfig
                                    }
                                }
                            }"""
                        ) { data ->
                            val targets = data.path("notificationRecords").path("pageItems")
                                .values().map { record ->
                                    record.path("channelConfig").path("target").asText()
                                }
                            assertEquals(
                                setOf("mail"),
                                targets.toSet()
                            )
                        }

                    }

                }
            }
        }
    }

    @Test
    fun `Getting the notifications linked to a promotion run`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    // Subscription at promotion level
                    val target = uid("pl-")
                    val subscriptionName = uid("pl-sub-")
                    eventSubscriptionService.subscribe(
                        name = subscriptionName,
                        channel = mockNotificationChannel,
                        channelConfig = MockNotificationChannelConfig(target),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN
                    )
                    // Creating a separate promotion
                    build {
                        promote(pl)
                    }
                    // Tracking a specific promotion
                    build {
                        val run = promote(pl)
                        // Looking at the records for THIS run to get its ID
                        val records = notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                eventEntityId = run.toProjectEntityID(),
                            )
                        ).pageItems
                        assertEquals(1, records.size)
                        val recordId = records.first().id
                        // Looking at the records for THIS run
                        run(
                            """{
                                notificationRecords(
                                    eventEntityType: PROMOTION_RUN,
                                    eventEntityId: ${run.id},
                                ) {
                                    pageItems {
                                        id
                                    }
                                }
                            }"""
                        ) { data ->
                            val record = data.path("notificationRecords").path("pageItems")
                                .path(0)
                            assertEquals(
                                recordId,
                                record.path("id").asText()
                            )
                        }
                    }
                }
            }
        }
    }

}