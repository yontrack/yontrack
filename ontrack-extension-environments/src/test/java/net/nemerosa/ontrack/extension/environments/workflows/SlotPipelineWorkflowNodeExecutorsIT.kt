package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannel
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannelConfig
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@QueueNoAsync
class SlotPipelineWorkflowNodeExecutorsIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowTestSupport: SlotWorkflowTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Autowired
    private lateinit var workflowNotificationChannel: WorkflowNotificationChannel

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Creating a pipeline into another slot for the same build using a workflow on deployed`() {
        asAdmin {
            var production: Slot? = null
            startNewTransaction {
                slotTestSupport.withSlot { staging ->
                    environmentTestSupport.withEnvironment { productionEnv ->
                        slotTestSupport.withSlot(
                            environment = productionEnv,
                            project = staging.project,
                        ) {
                            production = it
                            slotWorkflowService.addSlotWorkflow(
                                SlotWorkflow(
                                    pauseMs = 500,
                                    slot = staging,
                                    trigger = SlotPipelineStatus.DONE,
                                    workflow = WorkflowParser.parseYamlWorkflow(
                                        """
                                            name: Deployment to production
                                            nodes:
                                                - id: deploy
                                                  executorId: slot-pipeline-creation
                                                  data:
                                                    environment: ${productionEnv.name}
                                        """.trimIndent()
                                    )
                                )
                            )
                            // Creating, starting & finishing a pipeline
                            // This will trigger the workflow
                            slotTestSupport.createRunAndFinishDeployment(slot = staging)
                        }
                    }
                }
            } then {
                assertNotNull(production, "Production slot created")
                // We wait until the new pipeline is registered
                waitUntil(
                    message = "New pipeline registered in production",
                    timeout = 10.seconds,
                    interval = 1.seconds,
                ) {
                    inNewTransaction {
                        slotService.findPipelines(production!!).pageItems.isNotEmpty()
                    }
                }

                // Getting the new pipeline
                val productionPipeline = inNewTransaction {
                    slotService.findPipelines(production!!).pageItems.first()
                }
                assertEquals(SlotPipelineStatus.CANDIDATE, productionPipeline.status)
            }

        }

    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `On a promotion, running a workflow to put a pipeline into a slot`() {
        asAdmin {
            startNewTransaction {
                val slot = slotTestSupport.withSlot {}
                val build = slot.project.branch<Build> {
                    val pl = promotionLevel()
                    // Promotion subscription running a workflow which pushes the build into a slot
                    eventSubscriptionService.subscribe(
                        name = "Start pipeline on promotion",
                        channel = workflowNotificationChannel,
                        channelConfig = WorkflowNotificationChannelConfig(
                            pauseMs = 500,
                            workflow = WorkflowParser.parseYamlWorkflow(
                                """
                                    name: Start pipeline
                                    nodes:
                                      - id: pipeline
                                        executorId: slot-pipeline-creation
                                        data:
                                          environment: ${slot.environment.name}
                                          qualifier: null
                                """.trimIndent()
                            )
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "Test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    build {
                        promote(pl)
                    }
                }
                slot to build
            } then { (slot, build) ->
                // Waiting for this build to be into a pipeline
                waitUntil(
                    message = "Build into pipeline",
                    timeout = 10.seconds,
                    interval = 1.seconds,
                ) {
                    slotService.getCurrentPipeline(slot)?.build?.id == build.id
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `On a promotion, running a workflow to put a pipeline into a slot and send a link to this pipeline`() {
        val mockChannelTarget = uid("mock-")
        asAdmin {
            startNewTransaction {
                val slot = slotTestSupport.slot()
                val build = slot.project.branch<Build> {
                    val pl = promotionLevel()

                    // Promotion subscription running a workflow which pushes the build into a slot
                    eventSubscriptionService.subscribe(
                        name = "Start pipeline on promotion",
                        channel = workflowNotificationChannel,
                        channelConfig = WorkflowNotificationChannelConfig(
                            pauseMs = 500,
                            workflow = WorkflowParser.parseYamlWorkflow(
                                """
                                name: Start pipeline
                                nodes:
                                  - id: pipeline
                                    executorId: slot-pipeline-creation
                                    data:
                                        environment: ${slot.environment.name}
                                  - id: notification
                                    parents:
                                      - id: pipeline
                                    executorId: notification
                                    data:
                                        channel: mock
                                        channelConfig:
                                            target: $mockChannelTarget
                                            rendererType: html
                                        template: |
                                            Build ${'$'}{build} has started its deployment at ${'$'}{#.pipeline?id=workflow.pipeline.targetPipelineId}
                            """
                            ),
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "Test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    build {
                        // Promoting & launching the workflow
                        promote(pl)
                    }
                }
                slot to build
            } then { (slot, build) ->
                // Waiting for this build to be into a pipeline
                waitUntil(
                    message = "Build into pipeline",
                    timeout = 10.seconds,
                    interval = 1.seconds,
                ) {
                    slotService.getCurrentPipeline(slot)?.build?.id == build.id
                }

                // Pipeline
                val pipeline = slotService.getCurrentPipeline(slot)
                    ?: error("No current pipeline found")

                // Checking the message
                mockNotificationChannel.waitUntilReceivedMessage(
                    what = "Pipeline message",
                    target = mockChannelTarget,
                    expectedMessage = """
                        Build <a href="http://localhost:3000/build/${build.id}">${build.name}</a> has started its deployment at <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.fullName()}</a>
                    """.trimIndent().trim()
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `On a promotion, running a workflow to put a pipeline into a slot, deploying it and finishing it`() {
        asAdmin {
            val mockChannelTarget = uid("mock-")
            startNewTransaction {
                val slot = slotTestSupport.slot()
                val build = slot.project.branch<Build> {
                    val pl = promotionLevel()

                    // Promotion subscription running a workflow which pushes the build into a slot
                    eventSubscriptionService.subscribe(
                        name = "Start pipeline on promotion",
                        channel = workflowNotificationChannel,
                        channelConfig = WorkflowNotificationChannelConfig(
                            pauseMs = 500,
                            workflow = WorkflowParser.parseYamlWorkflow(
                                """
                                name: Start pipeline
                                nodes:
                                  - id: start
                                    executorId: slot-pipeline-creation
                                    data:
                                        environment: ${slot.environment.name}
                                  - id: deploying
                                    parents:
                                      - id: start
                                    executorId: slot-pipeline-deploying
                                    data: {}
                                  - id: deployment
                                    parents:
                                      - id: deploying
                                    executorId: notification
                                    data:
                                        channel: mock
                                        channelConfig:
                                            target: $mockChannelTarget
                                            rendererType: html
                                        template: |
                                            Build ${'$'}{build} has started its deployment at ${'$'}{#.pipeline?id=workflow.start.targetPipelineId}
                                  - id: deployed
                                    parents:
                                      - id: deployment
                                    executorId: slot-pipeline-deployed
                                    data: {}
                                  - id: deployed-message
                                    parents:
                                      - id: deployed
                                    executorId: notification
                                    data:
                                        channel: mock
                                        channelConfig:
                                            target: $mockChannelTarget
                                            rendererType: html
                                        template: |
                                            Build ${'$'}{build} has been deployed at ${'$'}{#.pipeline?id=workflow.start.targetPipelineId}
                            """
                            ),
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "Test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    build {
                        // Promoting & launching the workflow
                        promote(pl)
                    }
                }
                slot to build
            } then { (slot, build) ->

                // Waiting for this build to be into a pipeline
                // ... and this pipeline must be deployed
                waitUntil(
                    message = "Pipeline deployed",
                    timeout = 10.seconds,
                    interval = 1.seconds,
                ) {
                    val pipeline = slotService.getCurrentPipeline(slot)
                    pipeline?.build?.id == build.id && pipeline.status == SlotPipelineStatus.DONE
                }

                // Pipeline
                val pipeline = slotService.getCurrentPipeline(slot)
                    ?: error("No current pipeline found")

                // Checking the messages
                mockNotificationChannel.waitUntilReceivedCountMessages(
                    what = "Received messages",
                    target = mockChannelTarget,
                    expectedCount = 2,
                )
                val messages = mockNotificationChannel.targetMessages(mockChannelTarget)
                assertEquals(
                    listOf(
                        """
                            Build <a href="http://localhost:3000/build/${build.id}">${build.name}</a> has started its deployment at <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.fullName()}</a>
                        """.trimIndent().trim(),
                        """
                            Build <a href="http://localhost:3000/build/${build.id}">${build.name}</a> has been deployed at <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.fullName()}</a>
                        """.trimIndent().trim(),
                    ),
                    messages.map { it.trim() }
                )
            }
        }
    }

    @Test
    fun `Deployment workflow passes a context to notifications`() {
        asAdmin {
            val mockChannelTarget = uid("mock-")

            // Everything the notification reads - the slot, the build, the workflow registration and
            // the pipeline itself - is set up and committed *here*, before the transaction which
            // finishes the deployment and thereby triggers the workflow.
            //
            // Finishing a deployment starts its DONE workflows on the workflow engine's own threads,
            // in their own transactions: a workflow node cannot see the data of the transaction which
            // started it until that transaction commits. Creating the pipeline and finishing it in one
            // single transaction leaves the node racing that commit, and on a loaded runner the node
            // wins: it fails with "Slot pipeline ... was not found" and nothing is notified. See #1812,
            // which found the same shape in SlotPipelineChangelogIT.
            val pipeline = inNewTransaction {
                val slot = slotTestSupport.slot()
                val build = slot.project.branch<Build> {
                    build()
                }

                slotWorkflowService.addSlotWorkflow(
                    SlotWorkflow(
                        slot = slot,
                        trigger = SlotPipelineStatus.DONE,
                        workflow = WorkflowParser.parseYamlWorkflow(
                            """
                                name: Deployment is done
                                nodes:
                                    - id: deployed
                                      executorId: notification
                                      data:
                                        channel: mock
                                        channelConfig:
                                            target: $mockChannelTarget
                                        template: |
                                            Deployment ${'$'}{deployment} (id = ${'$'}{deployment.id}) is done
                            """.trimIndent()
                        )
                    )
                )

                // Starting the deployment, without finishing it yet
                slotService.startPipeline(slot, build)
            }

            // Finishing the deployment, which triggers the workflow
            inNewTransaction {
                slotTestSupport.runAndFinishDeployment(pipeline)
            }

            // Waits for the completion of the workflows, and checks that they actually succeeded:
            // a failed workflow is "finished" too, and would otherwise be diagnosed below as a
            // missing notification rather than as the node error it is
            slotWorkflowTestSupport.waitForSlotWorkflowsToSucceed(pipeline, SlotPipelineStatus.DONE)

            // Checks the messages. The workflow having succeeded, the notification is already
            // recorded - the channel is written before the node reports its success.
            val messages = mockNotificationChannel.targetMessages(mockChannelTarget)
            assertEquals(
                listOf(
                    """
                        Deployment ${pipeline.fullName()} (id = ${pipeline.id}) is done
                    """.trimIndent().trim(),
                ),
                messages.map { it.trim() }
            )
        }
    }
}