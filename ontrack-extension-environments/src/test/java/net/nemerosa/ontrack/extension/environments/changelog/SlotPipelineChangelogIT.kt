package net.nemerosa.ontrack.extension.environments.changelog

import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestSupport
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.time.ExperimentalTime

@QueueNoAsync
class SlotPipelineChangelogIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowTestSupport: SlotWorkflowTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Autowired
    private lateinit var mockNotificationChannel: MockNotificationChannel

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Sending a changelog since last deployment`() {
        val target = uid("t-")
        asAdmin {

            // Everything the changelog notification reads - the two builds, the SCM configuration,
            // the first (DONE) deployment and the second pipeline itself - is set up and committed
            // *here*, before the deployment which triggers the workflow.
            //
            // Finishing a deployment starts its DONE workflows on the workflow engine's own threads,
            // in their own transactions: a workflow node cannot see the data of the transaction which
            // started it until that transaction commits. Setting the second pipeline up and finishing
            // it in one single transaction leaves the node racing that commit, and on a loaded runner
            // the node wins: it fails with "Slot pipeline ... was not found" and nothing is notified.
            val secondDeployment = inNewTransaction {
                val slot = slotTestSupport.slot()
                val project = slot.project
                val branch = project.branch("main")

                val firstBuild = branch.build("1")

                // Finishing the first deployment
                val firstDeployment = slotService.startPipeline(slot, firstBuild)
                slotTestSupport.runAndFinishDeployment(firstDeployment)

                // Registering a workflow on DONE sending the changelog since the last deployment
                slotWorkflowService.addSlotWorkflow(
                    SlotWorkflow(
                        slot = slot,
                        trigger = SlotPipelineStatus.DONE,
                        workflow = WorkflowParser.parseYamlWorkflow(
                            """
                                name: Changelog notification
                                nodes:
                                  - id: changelog
                                    executorId: notification
                                    data:
                                      channel: mock
                                      channelConfig:
                                        target: $target
                                      template: |
                                        ${'$'}{deployment.changelog}
                                      
                            """.trimIndent()
                        )
                    )
                )

                // Creating a new build
                val secondBuild = branch.build("2")

                // Configuring the project/branch with an SCM
                mockSCMTester.withMockSCMRepository {
                    branch.configureMockSCMBranch()

                    // Configuring the SCM for a changelog between two builds
                    firstBuild.apply {
                        repositoryIssue("ISS-20", "Last issue before the change log", type = "defect")
                        withRepositoryCommit("ISS-20 Last commit before the change log")
                    }
                    secondBuild.apply {
                        repositoryIssue("ISS-22", "Some fixes are needed", type = "defect")
                        withRepositoryCommit("ISS-22 Fixing some bugs")
                    }
                }

                // Creating the second deployment, without running it yet
                slotService.startPipeline(slot, secondBuild)
            }

            // Running & finishing the second deployment, which triggers the changelog workflow
            inNewTransaction {
                slotTestSupport.runAndFinishDeployment(secondDeployment)
            }

            // Waits for the completion of the workflows, and checks that they actually succeeded:
            // a failed workflow is "finished" too, and would otherwise be diagnosed below as a
            // timeout on a notification which was never going to come
            slotWorkflowTestSupport.waitForSlotWorkflowsToSucceed(
                pipeline = secondDeployment,
                trigger = SlotPipelineStatus.DONE,
            )

            // Expecting a changelog. The workflow having succeeded, the notification is already
            // recorded - the channel is written before the node reports its success - so this wait
            // only has to observe it, and does not need the minute the previous wait here allowed.
            mockNotificationChannel.waitUntilReceivedMessage(
                what = "Waiting for notification to be sent",
                target = target,
                expectedMessage = """
                    * ISS-22 Some fixes are needed
                """.trimIndent().trim(),
            )
        }
    }

}
