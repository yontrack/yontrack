package net.nemerosa.ontrack.extension.environments

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotDeploymentCheckTest {

    @Test
    fun `An OK check is in the OK state`() {
        val check = SlotDeploymentCheck.ok()
        assertTrue(check.ok)
        assertEquals(SlotDeploymentCheckState.OK, check.state)
    }

    @Test
    fun `A NOK check is in the FAILED state`() {
        val check = SlotDeploymentCheck.nok("Rejected")
        assertFalse(check.ok)
        assertEquals(SlotDeploymentCheckState.FAILED, check.state)
    }

    @Test
    fun `A pending check is not OK and is in the PENDING state`() {
        val check = SlotDeploymentCheck.pending("No approval")
        assertFalse(check.ok, "A pending check still blocks")
        assertEquals(SlotDeploymentCheckState.PENDING, check.state)
        assertEquals("No approval", check.reason)
    }

    @Test
    fun `A check cannot be both OK and pending`() {
        assertFailsWith<IllegalArgumentException> {
            SlotDeploymentCheck(ok = true, overridden = false, reason = null, pending = true)
        }
    }

    @Test
    fun `The state of a list of checks is FAILED as soon as one has failed`() {
        assertEquals(
            SlotDeploymentCheckState.FAILED,
            SlotDeploymentCheckState.of(
                listOf(
                    SlotDeploymentCheck.ok(),
                    SlotDeploymentCheck.pending("Workflow is running"),
                    SlotDeploymentCheck.nok("Workflow is in error"),
                )
            )
        )
    }

    @Test
    fun `The state of a list of checks is PENDING when none has failed and one is pending`() {
        assertEquals(
            SlotDeploymentCheckState.PENDING,
            SlotDeploymentCheckState.of(
                listOf(
                    SlotDeploymentCheck.ok(),
                    SlotDeploymentCheck.pending("Workflow is running"),
                )
            )
        )
    }

    @Test
    fun `The state of a list of checks is OK when all are OK or when there is none`() {
        assertEquals(
            SlotDeploymentCheckState.OK,
            SlotDeploymentCheckState.of(listOf(SlotDeploymentCheck.ok(), SlotDeploymentCheck.ok()))
        )
        assertEquals(SlotDeploymentCheckState.OK, SlotDeploymentCheckState.of(emptyList()))
    }

}
