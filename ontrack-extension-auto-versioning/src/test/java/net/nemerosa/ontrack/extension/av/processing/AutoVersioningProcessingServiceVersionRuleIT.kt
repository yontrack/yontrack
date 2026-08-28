package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.time.ExperimentalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testing the opt-in version rules, which protect the target files against the version downgrade
 * caused by the re-promotion of an old build.
 */
@QueueNoAsync
class AutoVersioningProcessingServiceVersionRuleIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningProcessingService: AutoVersioningProcessingService

    @Autowired
    private lateinit var autoVersioningAuditService: AutoVersioningAuditService

    @Autowired
    private lateinit var autoVersioningAuditQueryService: AutoVersioningAuditQueryService

    @Autowired
    private lateinit var mockNotificationChannel: MockNotificationChannel

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @OptIn(ExperimentalTime::class)
    @Test
    fun `A downgrade is rejected and nothing is written`() {
        val notificationTarget = uid("n")
        withTarget(currentVersion = "2.0.0") { target, sourceProject ->
            target.subscribeToRejections(notificationTarget)

            val order = target.createOrder(
                sourceProject = sourceProject,
                targetVersion = "1.0.0",
                versionRule = "semver",
            )
            autoVersioningAuditService.onCreated(order)

            val outcome = autoVersioningProcessingService.process(order)
            assertEquals(AutoVersioningProcessingOutcome.REJECTED, outcome)

            assertEquals(
                "version = 2.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has not been changed"
            )
            assertNull(
                getRepositoryBranch("feature/version-1.0.0-*"),
                "No upgrade branch has been created"
            )

            val entry = autoVersioningAuditQueryService.findByUUID(target, order.uuid)
            assertNotNull(entry, "Audit entry found") {
                assertEquals(
                    AutoVersioningAuditState.PROCESSING_ABORTED,
                    it.mostRecentState.state,
                    "Processing has been aborted"
                )
                val message = it.mostRecentState.data["message"] ?: ""
                assertTrue(
                    "gradle.properties" in message,
                    "Audit message names the offending path: $message"
                )
                assertTrue("semver" in message, "Audit message names the rule: $message")
            }

            waitUntil(message = "Waiting for the rejection notification") {
                mockNotificationChannel.targetMessages(notificationTarget).isNotEmpty()
            }
            val message = mockNotificationChannel.targetMessages(notificationTarget).first()
            assertTrue("rejected" in message, "Rejection event has been sent: $message")
        }
    }

    @Test
    fun `An upgrade is accepted when the rule is set`() {
        withTarget(currentVersion = "1.0.0") { target, sourceProject ->
            val order = target.createOrder(
                sourceProject = sourceProject,
                targetVersion = "2.0.0",
                versionRule = "semver",
            )
            val outcome = autoVersioningProcessingService.process(order)
            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)
            assertEquals(
                "version = 2.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has been upgraded"
            )
        }
    }

    @Test
    fun `No check is performed when no rule is set`() {
        withTarget(currentVersion = "2.0.0") { target, sourceProject ->
            val order = target.createOrder(
                sourceProject = sourceProject,
                targetVersion = "1.0.0",
            )
            val outcome = autoVersioningProcessingService.process(order)
            assertEquals(
                AutoVersioningProcessingOutcome.CREATED,
                outcome,
                "Version rules are opt-in: the downgrade goes through when no rule is configured"
            )
        }
    }

    @Test
    fun `An unparseable version is rejected by default`() {
        withTarget(currentVersion = "some-sha") { target, sourceProject ->
            val order = target.createOrder(
                sourceProject = sourceProject,
                targetVersion = "1.0.0",
                versionRule = "semver",
            )
            autoVersioningAuditService.onCreated(order)
            val outcome = autoVersioningProcessingService.process(order)
            assertEquals(AutoVersioningProcessingOutcome.REJECTED, outcome)
        }
    }

    @Test
    fun `An unparseable version can be accepted using the rule configuration`() {
        withTarget(currentVersion = "some-sha") { target, sourceProject ->
            val order = target.createOrder(
                sourceProject = sourceProject,
                targetVersion = "1.0.0",
                versionRule = "semver",
                versionRuleConfig = mapOf("onUnparseable" to "ACCEPT").asJson(),
            )
            val outcome = autoVersioningProcessingService.process(order)
            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)
        }
    }

    @Test
    fun `A downgrade on an additional path rejects the whole order`() {
        withTarget(currentVersion = "1.0.0") { target, sourceProject ->
            repositoryFile(
                path = "other.properties",
                content = "version = 3.0.0",
            )

            val order = target.createOrder(
                sourceProject = sourceProject,
                targetVersion = "2.0.0",
                versionRule = "semver",
                additionalPaths = listOf(
                    AutoVersioningSourceConfigPath(
                        path = "other.properties",
                        regex = null,
                        property = "version",
                        propertyRegex = null,
                        propertyType = null,
                        versionSource = null,
                    )
                ),
            )
            autoVersioningAuditService.onCreated(order)

            val outcome = autoVersioningProcessingService.process(order)
            assertEquals(AutoVersioningProcessingOutcome.REJECTED, outcome)

            assertEquals(
                "version = 1.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "The upgradable path has not been changed either"
            )
            assertEquals(
                "version = 3.0.0",
                getRepositoryFile(path = "other.properties"),
                "The rejected path has not been changed"
            )
        }
    }

    /**
     * Creates a source project and a target branch backed by a mock SCM repository, whose
     * `gradle.properties` file contains the [currentVersion].
     */
    private fun withTarget(
        currentVersion: String,
        code: MockSCMTester.MockSCMRepositoryContext.(target: Branch, sourceProject: String) -> Unit,
    ) {
        asAdmin {
            val source = doCreateProject()
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()
                        repositoryFile(
                            path = "gradle.properties",
                            content = "version = $currentVersion",
                        )
                        code(this, source.name)
                    }
                }
            }
        }
    }

    private fun Branch.subscribeToRejections(notificationTarget: String) {
        eventSubscriptionService.subscribe(
            name = uid("s"),
            channel = mockNotificationChannel,
            channelConfig = MockNotificationChannelConfig(target = notificationTarget),
            projectEntity = this,
            keywords = null,
            origin = "test",
            contentTemplate = null,
            AutoVersioningEvents.AUTO_VERSIONING_REJECTED,
        )
    }

}
