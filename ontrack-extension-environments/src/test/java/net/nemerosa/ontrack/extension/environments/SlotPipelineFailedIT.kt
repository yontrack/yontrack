package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.events.EnvironmentsEvents
import net.nemerosa.ontrack.extension.environments.security.EnvironmentList
import net.nemerosa.ontrack.extension.environments.security.SlotPipelineFinish
import net.nemerosa.ontrack.extension.environments.security.SlotPipelineWorkflowRun
import net.nemerosa.ontrack.extension.environments.security.SlotView
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.getPipelineById
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestSupport
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.*

/**
 * The `FAILED` status of a slot pipeline (#1894): a deployment which was started and did not
 * make it. Terminal, reachable from `RUNNING` only, and not changing what the slot runs.
 */
@QueueNoAsync
@AsAdminTest
class SlotPipelineFailedIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowTestSupport: SlotWorkflowTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Test
    fun `A running pipeline can be marked as failed`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            val status = slotService.failPipeline(pipeline.id)
            assertTrue(status.ok, "Pipeline marked as failed")

            val failed = slotService.getPipelineById(pipeline.id)
            assertEquals(SlotPipelineStatus.FAILED, failed.status)
            assertNotNull(failed.end, "A failed pipeline has an end")

            val change = slotService.getPipelineChanges(failed).first()
            assertEquals(SlotPipelineChangeType.STATUS, change.type)
            assertEquals(SlotPipelineStatus.FAILED, change.status)
            assertEquals("Deployment failed", change.message)
            assertNull(change.overrideMessage)
        }
    }

    @Test
    fun `The failure message is recorded in the pipeline history`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            val status = slotService.failPipeline(pipeline.id, message = "Helm upgrade timed out")
            assertTrue(status.ok)
            assertEquals("Helm upgrade timed out", status.message)
            val change = slotService.getPipelineChanges(pipeline).first()
            assertEquals(SlotPipelineStatus.FAILED, change.status)
            assertEquals("Helm upgrade timed out", change.message)
        }
    }

    @Test
    fun `A blank failure message is replaced by the default one`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            assertTrue(slotService.failPipeline(pipeline.id, message = "  ").ok)
            val change = slotService.getPipelineChanges(pipeline).first()
            assertEquals("Deployment failed", change.message)
        }
    }

    @Test
    fun `A candidate pipeline cannot be marked as failed`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val status = slotService.failPipeline(pipeline.id)
            assertFalse(status.ok, "A candidate cannot fail")
            assertEquals("Only a running deployment can be marked as failed.", status.message)
            val unchanged = slotService.getPipelineById(pipeline.id)
            assertEquals(SlotPipelineStatus.CANDIDATE, unchanged.status)
            assertNull(unchanged.end)
        }
    }

    @Test
    fun `A deployed pipeline cannot be marked as failed`() {
        slotTestSupport.withFinishedDeployment { pipeline ->
            val status = slotService.failPipeline(pipeline.id)
            assertFalse(status.ok)
            assertEquals(SlotPipelineStatus.DONE, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A failed pipeline is terminal`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            assertTrue(slotService.failPipeline(pipeline.id).ok)

            // Not failed twice
            assertFalse(slotService.failPipeline(pipeline.id).ok)

            // Not finished, not even forced
            val finish = slotService.finishDeployment(pipeline.id, forcing = true, message = "Forcing")
            assertFalse(finish.ok, "A failed deployment cannot be forced to deployed")
            assertEquals("A failed deployment cannot be marked as deployed.", finish.message)

            // Not cancelled
            assertFailsWith<InputException> {
                slotService.cancelPipeline(slotService.getPipelineById(pipeline.id), "Cancelling")
            }

            assertEquals(SlotPipelineStatus.FAILED, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A failed pipeline does not change the last deployed pipeline`() {
        slotTestSupport.withSlot { slot ->
            val deployed = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val failing = slotTestSupport.createPipeline(slot = slot)
            assertTrue(slotService.runDeployment(failing.id, dryRun = false).ok)
            assertTrue(slotService.failPipeline(failing.id).ok)

            assertEquals(deployed.id, slotService.getLastDeployedPipeline(slot)?.id)
            // ... while it remains the slot's most recent one
            assertEquals(failing.id, slotService.getCurrentPipeline(slot)?.id)
        }
    }

    @Test
    fun `A failed pipeline does not prevent a new pipeline from starting`() {
        slotTestSupport.withSlot { slot ->
            val failing = slotTestSupport.createPipeline(slot = slot)
            assertTrue(slotService.runDeployment(failing.id, dryRun = false).ok)
            assertTrue(slotService.failPipeline(failing.id).ok)

            val next = slotTestSupport.createRunAndFinishDeployment(slot = slot)

            // The failure is kept as it was, not cancelled by the new pipeline
            assertEquals(SlotPipelineStatus.FAILED, slotService.getPipelineById(failing.id).status)
            assertEquals(next.id, slotService.getLastDeployedPipeline(slot)?.id)
        }
    }

    @Test
    fun `Failing a pipeline needs the same right as finishing it`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            asUser()
                .with(EnvironmentList::class.java)
                .withProjectFunction(pipeline.slot.project, ProjectView::class.java)
                .withProjectFunction(pipeline.slot.project, SlotView::class.java)
                .call {
                    assertFailsWith<AccessDeniedException> {
                        slotService.failPipeline(pipeline.id)
                    }
                }
            asUser()
                .with(EnvironmentList::class.java)
                .withProjectFunction(pipeline.slot.project, ProjectView::class.java)
                .withProjectFunction(pipeline.slot.project, SlotView::class.java)
                .withProjectFunction(pipeline.slot.project, SlotPipelineFinish::class.java)
                // Like finishing, failing starts the slot workflows of its trigger
                .withProjectFunction(pipeline.slot.project, SlotPipelineWorkflowRun::class.java)
                .call {
                    assertTrue(slotService.failPipeline(pipeline.id).ok)
                }
        }
    }

    @Test
    fun `Failing a pipeline is allowed to the automation role`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            asGlobalRole(Roles.GLOBAL_AUTOMATION) {
                assertTrue(slotService.failPipeline(pipeline.id).ok)
            }
        }
    }

    @Test
    fun `Failing a pipeline posts the slot-pipeline-failed event`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            assertTrue(slotService.failPipeline(pipeline.id).ok)
            val event = eventQueryService.getLastEvent(pipeline.build, EnvironmentsEvents.PIPELINE_FAILED)
            assertNotNull(event, "Failed event posted") {
                assertEquals("slot-pipeline-failed", it.eventType.id)
                assertEquals(pipeline.id, it.getValue(EnvironmentsEvents.EVENT_PIPELINE_ID))
            }
        }
    }

    @Test
    fun `Failing a pipeline runs the workflows of the FAILED trigger`() {
        slotWorkflowTestSupport.withSlotWorkflow(trigger = SlotPipelineStatus.FAILED) { slot, _ ->
            val pipeline = slotTestSupport.createPipeline(slot = slot)
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false).ok)
            // No FAILED workflow before the failure
            assertTrue(
                slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline).none {
                    it.slotWorkflow.trigger == SlotPipelineStatus.FAILED
                }
            )
            assertTrue(slotService.failPipeline(pipeline.id).ok)
            slotWorkflowTestSupport.waitForSlotWorkflowsToSucceed(
                pipeline = pipeline,
                trigger = SlotPipelineStatus.FAILED,
            )
        }
    }

    @Test
    fun `Finishing a pipeline does not run the workflows of the FAILED trigger`() {
        slotWorkflowTestSupport.withSlotWorkflow(trigger = SlotPipelineStatus.FAILED) { slot, _ ->
            val pipeline = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            assertTrue(
                slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline).isEmpty(),
                "No FAILED workflow for a deployed pipeline"
            )
        }
    }

    @Test
    fun `Marking a pipeline as failed using GraphQL`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            run(
                """
                    mutation {
                        failSlotPipeline(input: {
                            pipelineId: "${pipeline.id}",
                            message: "Smoke tests failed",
                        }) {
                            failStatus {
                                ok
                                message
                            }
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "failSlotPipeline") { node ->
                    assertEquals(true, node.path("failStatus").path("ok").asBoolean())
                    assertEquals("Smoke tests failed", node.path("failStatus").path("message").asText())
                }
                val failed = slotService.getPipelineById(pipeline.id)
                assertEquals(SlotPipelineStatus.FAILED, failed.status)
                assertEquals("Smoke tests failed", slotService.getPipelineChanges(failed).first().message)
            }
        }
    }

    @Test
    fun `Marking a candidate pipeline as failed using GraphQL is refused`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            run(
                """
                    mutation {
                        failSlotPipeline(input: {pipelineId: "${pipeline.id}"}) {
                            failStatus {
                                ok
                                message
                            }
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "failSlotPipeline") { node ->
                    assertEquals(false, node.path("failStatus").path("ok").asBoolean())
                    assertEquals(
                        "Only a running deployment can be marked as failed.",
                        node.path("failStatus").path("message").asText()
                    )
                }
                assertEquals(SlotPipelineStatus.CANDIDATE, slotService.getPipelineById(pipeline.id).status)
            }
        }
    }

    @Test
    fun `The FAILED status is exposed as a finished one using GraphQL`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            assertTrue(slotService.failPipeline(pipeline.id).ok)
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            status
                            finished
                            end
                        }
                    }
                """.trimIndent()
            ) { data ->
                val node = data.path("slotPipelineById")
                assertEquals("FAILED", node.path("status").asText())
                assertEquals(true, node.path("finished").asBoolean())
                assertTrue(node.path("end").asText().isNotBlank())
            }
        }
    }

}
