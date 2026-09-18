package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
import org.springframework.stereotype.Component
import java.util.concurrent.TimeoutException
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@Component
class SlotWorkflowTestSupport(
    private val slotTestSupport: SlotTestSupport,
    private val slotWorkflowService: SlotWorkflowService,
) : AbstractDSLTestSupport() {

    /**
     * Waits for all the workflows of a [pipeline] registered on a [trigger] to reach a *final* status.
     *
     * Note that `ERROR` and `STOPPED` are final statuses: a workflow which failed satisfies this wait.
     * When the test goes on to assert on something the workflow was supposed to produce - a notification,
     * a promotion - use [waitForSlotWorkflowsToSucceed] instead, or the workflow's failure is swallowed
     * here and reported much later as an unrelated timeout on the actual assertion.
     */
    fun waitForSlotWorkflowsToFinish(
        pipeline: SlotPipeline,
        trigger: SlotPipelineStatus,
        timeout: Duration = 10.seconds,
    ): List<SlotWorkflowInstance> {
        val slotWorkflowInstances = slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline)
            .filter { it.slotWorkflow.trigger == trigger }
        return slotWorkflowInstances.map { slotWorkflowInstance ->
            waitForSlotWorkflowInstanceToFinish(slotWorkflowInstance, timeout)
        }
    }

    /**
     * Waits for all the workflows of a [pipeline] registered on a [trigger] to finish, and checks that
     * they all succeeded.
     *
     * Fails - naming the offending nodes and their errors - rather than letting a failed workflow be
     * diagnosed later as a timeout on whatever the workflow was supposed to produce.
     */
    fun waitForSlotWorkflowsToSucceed(
        pipeline: SlotPipeline,
        trigger: SlotPipelineStatus,
        timeout: Duration = 10.seconds,
    ) {
        val instances = waitForSlotWorkflowsToFinish(pipeline, trigger, timeout)
        if (instances.isEmpty()) {
            fail("No slot workflow was registered on $trigger for pipeline ${pipeline.id}.")
        }
        instances.forEach { instance ->
            val workflowInstance = instance.workflowInstance
            if (workflowInstance.status != WorkflowInstanceStatus.SUCCESS) {
                fail("Slot workflow did not succeed: ${describe(workflowInstance)}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun waitForSlotWorkflowInstanceToFinish(
        slotWorkflowInstance: SlotWorkflowInstance,
        timeout: Duration = 10.seconds,
    ): SlotWorkflowInstance {
        try {
            waitUntil(
                message = "Slot workflow instance to finish: ${slotWorkflowInstance.id}",
                interval = 1.seconds,
                timeout = timeout,
            ) {
                slotWorkflowService.getSlotWorkflowInstanceById(slotWorkflowInstance.id).workflowInstance.status.finished
            }
        } catch (ex: TimeoutException) {
            val current = slotWorkflowService.getSlotWorkflowInstanceById(slotWorkflowInstance.id)
            fail("Slot workflow did not finish after $timeout: ${describe(current.workflowInstance)}", ex)
        }
        return slotWorkflowService.getSlotWorkflowInstanceById(slotWorkflowInstance.id)
    }

    /**
     * One-line description of a workflow instance and of each of its nodes, for a failure message.
     */
    private fun describe(workflowInstance: WorkflowInstance): String {
        val nodes = workflowInstance.nodesExecutions.joinToString(", ") { node ->
            val error = node.error?.let { " ($it)" } ?: ""
            "${node.id}=${node.status}$error"
        }
        return "instance ${workflowInstance.id} [${workflowInstance.workflow.name}] " +
                "status=${workflowInstance.status} nodes=[$nodes]"
    }

    fun withSlotWorkflow(
        trigger: SlotPipelineStatus,
        waitMs: Int = 0,
        error: Boolean = false,
        code: (slot: Slot, slotWorkflow: SlotWorkflow) -> Unit
    ) {
        slotTestSupport.withSlot { slot ->

            val testWorkflow = SlotWorkflowTestFixtures.testWorkflow(
                waitMs = waitMs,
                error = error,
            )
            val slotWorkflow = SlotWorkflow(
                slot = slot,
                trigger = trigger,
                workflow = testWorkflow,
            )
            slotWorkflowService.addSlotWorkflow(slotWorkflow)

            code(slot, slotWorkflow)
        }
    }

}
