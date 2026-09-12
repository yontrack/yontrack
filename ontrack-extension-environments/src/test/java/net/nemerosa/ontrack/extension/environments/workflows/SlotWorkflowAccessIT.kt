package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * A [SlotWorkflow] carries the full workflow definition, so the two by-id getters are read-protected
 * like every other slot-side read - see #1739.
 */
@QueueNoAsync
@AsAdminTest
class SlotWorkflowAccessIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Test
    fun `Getting a slot workflow by id is granted to a user with the slot view right`() {
        slotTestSupport.withSlot { slot ->
            val slotWorkflow = SlotWorkflowTestFixtures.slotWorkflow(slot)
            slotWorkflowService.addSlotWorkflow(slotWorkflow)
            assertEquals(
                slotWorkflow.workflow.name,
                slotWorkflowService.getSlotWorkflowById(slotWorkflow.id).workflow.name,
            )
            assertNotNull(slotWorkflowService.findSlotWorkflowById(slotWorkflow.id))
        }
    }

    @Test
    fun `Getting a slot workflow by id is denied to a user without the slot view right`() {
        slotTestSupport.withSlot { slot ->
            val slotWorkflow = SlotWorkflowTestFixtures.slotWorkflow(slot)
            slotWorkflowService.addSlotWorkflow(slotWorkflow)
            asUser {
                assertFailsWith<AccessDeniedException> {
                    slotWorkflowService.getSlotWorkflowById(slotWorkflow.id)
                }
                assertFailsWith<AccessDeniedException> {
                    slotWorkflowService.findSlotWorkflowById(slotWorkflow.id)
                }
            }
        }
    }
}
