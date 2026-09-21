package net.nemerosa.ontrack.extension.workflows.execution.core

import tools.jackson.databind.JsonNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.workflows.WorkflowsExtensionFeature
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.AbstractTypedWorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.model.docs.Documentation
import org.springframework.stereotype.Component

@Component
@Documentation(PauseWorkflowNodeExecutorData::class)
class PauseWorkflowNodeExecutor(
    feature: WorkflowsExtensionFeature,
) : AbstractTypedWorkflowNodeExecutor<PauseWorkflowNodeExecutorData>(
    feature,
    "pause",
    "Pause",
    PauseWorkflowNodeExecutorData::class
) {

    override fun validate(data: JsonNode) {}

    override fun execute(
        workflowInstance: WorkflowInstance,
        data: PauseWorkflowNodeExecutorData,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit
    ): WorkflowNodeExecutorResult {
        runBlocking {
            delay(data.pauseMs)
        }
        return WorkflowNodeExecutorResult.success(output = null)
    }

}