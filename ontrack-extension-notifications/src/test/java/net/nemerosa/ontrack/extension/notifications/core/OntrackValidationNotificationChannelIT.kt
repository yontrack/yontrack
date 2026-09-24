package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OntrackValidationNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var ontrackValidationNotificationChannel: OntrackValidationNotificationChannel

    @Test
    fun `Validation using a notification using full parameters`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()

                    eventSubscriptionService.subscribe(
                        name = uid("p"),
                        projectEntity = pl,
                        channel = ontrackValidationNotificationChannel,
                        channelConfig = OntrackValidationNotificationChannelConfig(
                            project = "${"$"}{project}",
                            branch = "${"$"}{branch}",
                            build = "${"$"}{build}",
                            validation = vs.name,
                        ),
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Validation of ${"$"}{build}",
                        eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                    )


                    build {
                        promote(pl)

                        // Checks that the validation has been set
                        val run = structureService.getValidationRunsForValidationStamp(vs, 0, 10).firstOrNull()
                        assertNotNull(run, "Build has been validated") {
                            assertEquals(
                                ValidationRunStatusID.STATUS_PASSED,
                                it.lastStatus.statusID,
                            )
                            assertEquals(
                                "Validation of $name",
                                it.lastStatus.description
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Validation using a notification using only required parameters`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()

                    eventSubscriptionService.subscribe(
                        name = uid("p"),
                        projectEntity = pl,
                        channel = ontrackValidationNotificationChannel,
                        channelConfig = OntrackValidationNotificationChannelConfig(
                            validation = vs.name,
                        ),
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Validation of ${"$"}{build}",
                        eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                    )


                    build {
                        promote(pl)

                        // Checks that the validation has been set
                        val run = structureService.getValidationRunsForValidationStamp(vs, 0, 10).firstOrNull()
                        assertNotNull(run) {
                            assertEquals(
                                ValidationRunStatusID.STATUS_PASSED,
                                it.lastStatus.statusID,
                            )
                            assertEquals(
                                "Validation of $name",
                                it.lastStatus.description
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Validation run time using no template`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()

                    eventSubscriptionService.subscribe(
                        name = uid("p"),
                        projectEntity = pl,
                        channel = ontrackValidationNotificationChannel,
                        channelConfig = OntrackValidationNotificationChannelConfig(
                            validation = vs.name,
                            runTime = "30",
                        ),
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Validation of ${"$"}{build}",
                        eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                    )


                    build {
                        promote(pl)

                        // Checks that the validation has been set
                        val run = structureService.getValidationRunsForValidationStamp(vs, 0, 10).firstOrNull()
                        assertNotNull(run) {
                            assertEquals(
                                ValidationRunStatusID.STATUS_PASSED,
                                it.lastStatus.statusID,
                            )
                            assertEquals(
                                "Validation of $name",
                                it.lastStatus.description
                            )
                            assertEquals(
                                30,
                                runInfoService.getRunInfo(it)?.runTime
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Validation status defaults to passed when not configured`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()

                    eventSubscriptionService.subscribe(
                        name = uid("p"),
                        projectEntity = pl,
                        channel = ontrackValidationNotificationChannel,
                        channelConfig = OntrackValidationNotificationChannelConfig(
                            validation = vs.name,
                        ),
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Validation of ${"$"}{build}",
                        eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                    )

                    build {
                        promote(pl)

                        val run = structureService.getValidationRunsForValidationStamp(vs, 0, 10).firstOrNull()
                        assertNotNull(run) {
                            assertEquals(
                                ValidationRunStatusID.STATUS_PASSED,
                                it.lastStatus.statusID,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Validation status using a fixed value`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()

                    eventSubscriptionService.subscribe(
                        name = uid("p"),
                        projectEntity = pl,
                        channel = ontrackValidationNotificationChannel,
                        channelConfig = OntrackValidationNotificationChannelConfig(
                            validation = vs.name,
                            status = "FAILED",
                        ),
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Validation of ${"$"}{build}",
                        eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
                    )

                    build {
                        promote(pl)

                        val run = structureService.getValidationRunsForValidationStamp(vs, 0, 10).firstOrNull()
                        assertNotNull(run) {
                            assertEquals(
                                ValidationRunStatusID.STATUS_FAILED,
                                it.lastStatus.statusID,
                            )
                        }
                    }
                }
            }
        }
    }

}