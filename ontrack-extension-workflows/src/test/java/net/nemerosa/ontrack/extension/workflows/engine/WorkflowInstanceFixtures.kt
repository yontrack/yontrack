package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowFixtures
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.model.events.MockEventType
import net.nemerosa.ontrack.model.trigger.TestTrigger
import net.nemerosa.ontrack.model.trigger.TestTriggerData
import net.nemerosa.ontrack.model.trigger.createTriggerData
import java.time.LocalDateTime

object WorkflowInstanceFixtures {

    fun simpleLinear(
        timestamp: LocalDateTime = Time.now(),
    ): WorkflowInstance {
        val workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
        // Event
        val event = MockEventType.serializedMockEvent("Some text")
        return createInstance(
            workflow = workflow,
            event = event,
            triggerData = TestTrigger().createTriggerData(TestTriggerData()),
            contexts = emptyMap(),
            timestamp = timestamp,
        )
    }
}