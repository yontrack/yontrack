package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordResult
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
class YontrackPromotionNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var yontrackPromotionNotificationChannel: YontrackPromotionNotificationChannel

    @Autowired
    private lateinit var notificationRecordingService: NotificationRecordingService

    @Test
    fun `Promotion using a notification using full parameters`() {
        project {
            branch {
                val pl = promotionLevel()
                val targetPl = promotionLevel()

                eventSubscriptionService.subscribe(
                    name = uid("s"),
                    projectEntity = pl,
                    channel = yontrackPromotionNotificationChannel,
                    channelConfig = YontrackPromotionNotificationChannelConfig(
                        project = $$"${project}",
                        branch = $$"${branch}",
                        build = $$"${build}",
                        promotion = targetPl.name,
                    ),
                    keywords = null,
                    origin = "test",
                    contentTemplate = $$"Promotion of ${build}",
                    eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                )

                build {
                    promote(pl)

                    // Checks that the target promotion has been set
                    val run = structureService.getPromotionRunsForBuildAndPromotionLevel(this, targetPl).firstOrNull()
                    assertNotNull(run, "Build has been promoted") {
                        assertEquals(
                            "Promotion of $name",
                            it.description
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Promotion using a notification using only required parameters`() {
        project {
            branch {
                val pl = promotionLevel()
                val targetPl = promotionLevel()

                eventSubscriptionService.subscribe(
                    name = uid("s"),
                    projectEntity = pl,
                    channel = yontrackPromotionNotificationChannel,
                    channelConfig = YontrackPromotionNotificationChannelConfig(
                        promotion = targetPl.name,
                    ),
                    keywords = null,
                    origin = "test",
                    contentTemplate = $$"Promotion of ${build}",
                    eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                )

                build {
                    promote(pl)

                    // Checks that the target promotion has been set
                    val run = structureService.getPromotionRunsForBuildAndPromotionLevel(this, targetPl).firstOrNull()
                    assertNotNull(run, "Build has been promoted") {
                        assertEquals(
                            "Promotion of $name",
                            it.description
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Promotion using a notification with fields`() {
        project {
            branch {
                val pl = promotionLevel()
                val targetPl = promotionLevel()

                eventSubscriptionService.subscribe(
                    name = uid("s"),
                    projectEntity = pl,
                    channel = yontrackPromotionNotificationChannel,
                    channelConfig = YontrackPromotionNotificationChannelConfig(
                        promotion = targetPl.name,
                        fields = listOf(
                            YontrackPromotionNotificationChannelConfigField(
                                name = "report",
                                value = $$"Build: ${build}",
                            )
                        ),
                    ),
                    keywords = null,
                    origin = "test",
                    contentTemplate = null,
                    eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                )

                build {
                    promote(pl)

                    // Checks that the target promotion has been set with the field value
                    val run = structureService.getPromotionRunsForBuildAndPromotionLevel(this, targetPl).firstOrNull()
                    assertNotNull(run, "Build has been promoted") {
                        val reportField = it.fieldValues.find { fv -> fv.name == "report" }
                        assertNotNull(reportField, "report field is set") { fv ->
                            assertEquals("Build: $name", fv.value?.asText())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Waiting ends in error when a notification of the promotion failed`() {
        project {
            branch {
                val promotionLevel = promotionLevel()
                // Ends in error, because the promotion level it targets does not exist
                eventSubscriptionService.subscribe(
                    name = uid("s"),
                    projectEntity = promotionLevel,
                    channel = yontrackPromotionNotificationChannel,
                    channelConfig = YontrackPromotionNotificationChannelConfig(promotion = "not-existing"),
                    keywords = null,
                    origin = "test",
                    contentTemplate = null,
                    eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                )
                build {
                    val config = YontrackPromotionNotificationChannelConfig(
                        promotion = promotionLevel.name,
                        waitForPromotion = true,
                    )
                    val published = yontrackPromotionNotificationChannel.publish(
                        recordId = "1",
                        config = config,
                        event = eventFactory.newBuild(this),
                        context = emptyMap(),
                        template = null,
                    ) { it }
                    assertEquals(NotificationResultType.ASYNC, published.type)

                    val result = yontrackPromotionNotificationChannel.getNotificationResult(
                        NotificationRecord(
                            id = uid("r"),
                            source = null,
                            timestamp = Time.now,
                            channel = yontrackPromotionNotificationChannel.type,
                            channelConfig = config.asJson(),
                            event = emptyMap<String, String>().asJson(),
                            result = NotificationRecordResult(
                                type = published.type,
                                message = null,
                                output = published.output?.asJson(),
                            ),
                        )
                    )

                    assertEquals(NotificationResultType.ERROR, result?.type)
                }
            }
        }
    }

    @Test
    fun `A configured build rendered blank ends in error and promotes nothing`() {
        assertBlankTargetFails(
            field = "build",
            config = { promotion -> YontrackPromotionNotificationChannelConfig(build = BLANK, promotion = promotion) },
        )
    }

    @Test
    fun `A configured branch rendered blank ends in error and promotes nothing`() {
        assertBlankTargetFails(
            field = "branch",
            config = { promotion -> YontrackPromotionNotificationChannelConfig(branch = BLANK, promotion = promotion) },
        )
    }

    @Test
    fun `A configured project rendered blank ends in error and promotes nothing`() {
        assertBlankTargetFails(
            field = "project",
            config = { promotion -> YontrackPromotionNotificationChannelConfig(project = BLANK, promotion = promotion) },
        )
    }

    @Test
    fun `Absent targets use the entities of the event`() {
        assertEventBuildPromoted { promotion ->
            YontrackPromotionNotificationChannelConfig(promotion = promotion)
        }
    }

    @Test
    fun `Blank targets in the configuration use the entities of the event`() {
        assertEventBuildPromoted { promotion ->
            YontrackPromotionNotificationChannelConfig(
                project = "",
                branch = " ",
                build = "  ",
                promotion = promotion,
            )
        }
    }

    private fun assertBlankTargetFails(
        field: String,
        config: (promotion: String) -> YontrackPromotionNotificationChannelConfig,
    ) {
        withBuildAndPromotion { build, promotionLevel ->
            val result = yontrackPromotionNotificationChannel.publish(
                recordId = "1",
                config = config(promotionLevel.name),
                event = eventFactory.newBuild(build),
                context = emptyMap(),
                template = null,
            ) { it }

            assertEquals(NotificationResultType.ERROR, result.type)
            assertEquals(
                "The $field of the promotion is configured but was rendered blank. Nothing has been promoted.",
                result.message,
            )
            assertTrue(
                structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel).isEmpty(),
                "Nothing has been promoted",
            )
        }
    }

    private fun assertEventBuildPromoted(
        config: (promotion: String) -> YontrackPromotionNotificationChannelConfig,
    ) {
        withBuildAndPromotion { build, promotionLevel ->
            val result = yontrackPromotionNotificationChannel.publish(
                recordId = "1",
                config = config(promotionLevel.name),
                event = eventFactory.newBuild(build),
                context = emptyMap(),
                template = null,
            ) { it }

            assertEquals(NotificationResultType.OK, result.type)
            assertEquals(
                1,
                structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel).size,
                "The build of the event has been promoted",
            )
        }
    }

    private fun withBuildAndPromotion(code: (build: Build, promotionLevel: PromotionLevel) -> Unit) {
        project {
            branch {
                val promotionLevel = promotionLevel()
                build {
                    code(this, promotionLevel)
                }
            }
        }
    }

    companion object {
        /**
         * Template rendered blank, because the build has no such link.
         */
        private const val BLANK = $$"${build.linked?project=not-linked}"
    }

}