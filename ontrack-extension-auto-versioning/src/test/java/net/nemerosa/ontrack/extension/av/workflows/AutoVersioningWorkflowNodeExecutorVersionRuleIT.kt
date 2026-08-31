package net.nemerosa.ontrack.extension.av.workflows

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfig
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResultType
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.time.ExperimentalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testing the version rules on the auto-versioning driven by an `auto-versioning` workflow node.
 *
 * The rule is declared on the node itself: nothing is inherited from the target branch's
 * auto-versioning configuration, which the node never reads.
 */
@QueueNoAsync
class AutoVersioningWorkflowNodeExecutorVersionRuleIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningWorkflowNodeExecutor: AutoVersioningWorkflowNodeExecutor

    @Autowired
    private lateinit var autoVersioningAuditQueryService: AutoVersioningAuditQueryService

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var serializableEventService: SerializableEventService

    @Autowired
    private lateinit var mockNotificationChannel: MockNotificationChannel

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @OptIn(ExperimentalTime::class)
    @Test
    fun `A downgrade is rejected when the node declares a version rule`() {
        val notificationTarget = uid("n")
        withTarget(currentVersion = "2.0.0") { target, sourceBuild ->
            target.subscribeToRejections(notificationTarget)

            val result = execute(
                sourceBuild = sourceBuild,
                data = data(
                    target = target,
                    targetVersion = "1.0.0",
                    versionRule = "semver",
                ),
            )

            assertEquals(WorkflowNodeExecutorResultType.ERROR, result.type)

            assertEquals(
                "version = 2.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has not been changed"
            )
            assertNull(
                getRepositoryBranch("feature/version-1.0.0-*"),
                "No upgrade branch has been created"
            )

            val orderId = assertNotNull(
                result.output?.path("autoVersioningOrderId")?.asText(),
                "Order ID returned in the output"
            )
            val entry = assertNotNull(
                autoVersioningAuditQueryService.findByUUID(target, orderId),
                "Audit entry found"
            )
            assertEquals(
                AutoVersioningAuditState.PROCESSING_ABORTED,
                entry.mostRecentState.state,
                "Processing has been aborted"
            )

            waitUntil(message = "Waiting for the rejection notification") {
                mockNotificationChannel.targetMessages(notificationTarget).isNotEmpty()
            }
            val message = mockNotificationChannel.targetMessages(notificationTarget).first()
            assertTrue("rejected" in message, "Rejection event has been sent: $message")
        }
    }

    @Test
    fun `An upgrade is accepted when the node declares a version rule`() {
        withTarget(currentVersion = "1.0.0") { target, sourceBuild ->
            val result = execute(
                sourceBuild = sourceBuild,
                data = data(
                    target = target,
                    targetVersion = "2.0.0",
                    versionRule = "semver",
                ),
            )

            assertEquals(WorkflowNodeExecutorResultType.SUCCESS, result.type)
            assertEquals(
                "version = 2.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has been upgraded"
            )
        }
    }

    @Test
    fun `An unparseable version is rejected by default`() {
        withTarget(currentVersion = "some-sha") { target, sourceBuild ->
            val result = execute(
                sourceBuild = sourceBuild,
                data = data(
                    target = target,
                    targetVersion = "1.0.0",
                    versionRule = "semver",
                ),
            )

            assertEquals(WorkflowNodeExecutorResultType.ERROR, result.type)
            assertEquals(
                "version = some-sha",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has not been changed"
            )
        }
    }

    @Test
    fun `The rule configuration declared on the node is used`() {
        withTarget(currentVersion = "some-sha") { target, sourceBuild ->
            val result = execute(
                sourceBuild = sourceBuild,
                data = data(
                    target = target,
                    targetVersion = "1.0.0",
                    versionRule = "semver",
                    versionRuleConfig = mapOf("onUnparseable" to "ACCEPT"),
                ),
            )

            assertEquals(
                WorkflowNodeExecutorResultType.SUCCESS,
                result.type,
                "The unparseable current version is accepted because the node says so"
            )
        }
    }

    @Test
    fun `A downgrade goes through when the node declares no version rule`() {
        withTarget(currentVersion = "2.0.0") { target, sourceBuild ->
            val result = execute(
                sourceBuild = sourceBuild,
                data = data(
                    target = target,
                    targetVersion = "1.0.0",
                ),
            )

            assertEquals(
                WorkflowNodeExecutorResultType.SUCCESS,
                result.type,
                "Version rules are opt-in: the downgrade goes through when the node declares no rule"
            )
            assertEquals(
                "version = 1.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has been downgraded"
            )
        }
    }

    @Test
    fun `The version rule of the target branch is not inherited by the node`() {
        withTarget(currentVersion = "2.0.0") { target, sourceBuild ->
            // The target branch is guarded for its promotion-driven auto-versioning...
            autoVersioningConfigurationService.setupAutoVersioning(
                target,
                AutoVersioningConfig(
                    configurations = listOf(
                        AutoVersioningTestFixtures.sourceConfig(
                            sourceProject = sourceBuild.project.name,
                            sourceBranch = sourceBuild.branch.name,
                            sourcePromotion = "GOLD",
                            versionRule = "semver",
                        )
                    )
                )
            )

            // ... but the workflow node carries its own configuration and declares no rule
            val result = execute(
                sourceBuild = sourceBuild,
                data = data(
                    target = target,
                    targetVersion = "1.0.0",
                ),
            )

            assertEquals(
                WorkflowNodeExecutorResultType.SUCCESS,
                result.type,
                "The rule of the target branch does not apply to the workflow node"
            )
            assertEquals(
                "version = 1.0.0",
                getRepositoryFile(path = "gradle.properties"),
                "Target file has been downgraded"
            )
        }
    }

    private fun data(
        target: Branch,
        targetVersion: String,
        versionRule: String? = null,
        versionRuleConfig: Map<String, String>? = null,
    ) = AutoVersioningWorkflowNodeExecutorData(
        targetProject = target.project.name,
        targetBranch = target.name,
        targetVersion = targetVersion,
        targetPath = "gradle.properties",
        targetProperty = "version",
        upgradeBranchPattern = "feature/version-<version>",
        versionRule = versionRule,
        versionRuleConfig = versionRuleConfig?.asJson(),
    )

    /**
     * Runs the `auto-versioning` node of a one-node workflow, going through the node data
     * deserialization so that the whole path from the workflow definition to the order is covered.
     */
    private fun execute(
        sourceBuild: Build,
        data: AutoVersioningWorkflowNodeExecutorData,
    ) = autoVersioningWorkflowNodeExecutor.execute(
        workflowInstance = WorkflowInstance(
            id = uid("i"),
            timestamp = Time.now,
            workflow = Workflow(
                name = "Test",
                nodes = listOf(
                    WorkflowNode(
                        id = "av",
                        executorId = "auto-versioning",
                        data = data.asJson(),
                    )
                )
            ),
            event = serializableEventService.dehydrate(eventFactory.newBuild(sourceBuild)),
            contexts = emptyMap(),
            nodesExecutions = emptyList(),
        ),
        workflowNodeId = "av",
    ) {}

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

    /**
     * Creates a source build and a target branch backed by a mock SCM repository, whose
     * `gradle.properties` file contains the [currentVersion].
     */
    private fun withTarget(
        currentVersion: String,
        code: MockSCMTester.MockSCMRepositoryContext.(target: Branch, sourceBuild: Build) -> Unit,
    ) {
        asAdmin {
            val sourceBuild = project<Build> {
                branch<Build> {
                    build("1.0.0")
                }
            }
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()
                        repositoryFile(
                            path = "gradle.properties",
                            content = "version = $currentVersion",
                        )
                        code(this, sourceBuild)
                    }
                }
            }
        }
    }

}
