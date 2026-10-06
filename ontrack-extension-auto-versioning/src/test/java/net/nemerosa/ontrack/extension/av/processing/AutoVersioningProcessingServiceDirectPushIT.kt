package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Testing the direct push mode of the auto-versioning, and in particular the cleanup
 * of the intermediate upgrade branch.
 */
@QueueNoAsync
class AutoVersioningProcessingServiceDirectPushIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningProcessingService: AutoVersioningProcessingService

    @Autowired
    private lateinit var mockNotificationChannel: MockNotificationChannel

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @Test
    fun `Upgrade branch is deleted after a direct push`() {
        asAdmin {
            project {
                val source = this
                mockSCMTester.withMockSCMRepository {
                    project {
                        branch {
                            configureMockSCMBranch()
                            repositoryFile(
                                path = "gradle.properties",
                                content = "version = 1.0.0",
                            )

                            val order = createOrder(
                                sourceProject = source.name,
                                targetVersion = "2.0.0",
                                upgradeBranchPattern = "feature/version-<version>",
                                pushMode = AutoVersioningPushMode.PUSH,
                            )

                            val outcome = autoVersioningProcessingService.process(order)
                            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)

                            assertEquals(
                                "version = 2.0.0",
                                getRepositoryFile(path = "gradle.properties"),
                                "Version has been pushed to the target branch"
                            )

                            assertNull(
                                getRepositoryBranch("feature/version-2.0.0-*"),
                                "Upgrade branch has been deleted after the push"
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Success notification of a direct push shows the pushed commit and no pull request`() {
        val notificationTarget = uid("n")
        asAdmin {
            project {
                val source = this
                mockSCMTester.withMockSCMRepository {
                    project {
                        branch {
                            configureMockSCMBranch()
                            repositoryFile(
                                path = "gradle.properties",
                                content = "version = 1.0.0",
                            )
                            eventSubscriptionService.subscribe(
                                name = uid("s"),
                                channel = mockNotificationChannel,
                                channelConfig = MockNotificationChannelConfig(
                                    target = notificationTarget,
                                    rendererType = "markdown",
                                ),
                                projectEntity = this,
                                keywords = null,
                                origin = "test",
                                contentTemplate = null,
                                AutoVersioningEvents.AUTO_VERSIONING_SUCCESS,
                            )

                            val order = createOrder(
                                sourceProject = source.name,
                                targetVersion = "2.0.0",
                                upgradeBranchPattern = "feature/version-<version>",
                                pushMode = AutoVersioningPushMode.PUSH,
                            )

                            val outcome = autoVersioningProcessingService.process(order)
                            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)

                            waitUntil(message = "Waiting for the success notification") {
                                mockNotificationChannel.targetMessages(notificationTarget).isNotEmpty()
                            }
                            val message = mockNotificationChannel.targetMessages(notificationTarget).first()
                            val pushedCommit = assertNotNull(
                                getRepositoryBranch("main")?.lastCommit,
                                "The push has created a commit on the target branch"
                            )
                            assertTrue(
                                "[Commit ${pushedCommit.shortId}](${pushedCommit.link})" in message,
                                "The notification links to the pushed commit: $message"
                            )
                            assertFalse(
                                "Pull request" in message,
                                "The notification of a direct push does not mention a pull request: $message"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Upgrade branch is kept in PR mode`() {
        asAdmin {
            project {
                val source = this
                mockSCMTester.withMockSCMRepository {
                    project {
                        branch {
                            configureMockSCMBranch()
                            repositoryFile(
                                path = "gradle.properties",
                                content = "version = 1.0.0",
                            )

                            val order = createOrder(
                                sourceProject = source.name,
                                targetVersion = "2.0.0",
                                upgradeBranchPattern = "feature/version-<version>",
                                pushMode = AutoVersioningPushMode.PR,
                            )

                            val outcome = autoVersioningProcessingService.process(order)
                            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)

                            assertNotNull(
                                getRepositoryBranch("feature/version-2.0.0-*"),
                                "Upgrade branch is kept as the head of the pull request"
                            )
                        }
                    }
                }
            }
        }
    }

}
