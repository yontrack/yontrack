package net.nemerosa.ontrack.extension.github.ingestion.processing

import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowJobAction
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowJobPayload
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorkflowJobPayloadTest {

    @Test
    fun `Parsing of queued working job`() {
        val json = TestUtils.resourceJson("/ingestion/workflow_job_queued.json")
        val payload = json.parse<WorkflowJobPayload>()
        assertEquals(WorkflowJobAction.queued, payload.action)
    }

}