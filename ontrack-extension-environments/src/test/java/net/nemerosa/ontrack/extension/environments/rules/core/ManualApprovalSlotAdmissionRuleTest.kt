package net.nemerosa.ontrack.extension.environments.rules.core

import io.mockk.mockk
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTypedData
import net.nemerosa.ontrack.extension.environments.SlotDeploymentCheckState
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ManualApprovalSlotAdmissionRuleTest {

    private val rule = ManualApprovalSlotAdmissionRule(securityService = mockk())
    private val config = ManualApprovalSlotAdmissionRuleConfig(message = "Approval needed")

    private fun check(approval: Boolean?) = rule.isBuildDeployable(
        pipeline = mockk<SlotPipeline>(),
        admissionRuleConfig = mockk<SlotAdmissionRuleConfig>(),
        ruleConfig = config,
        ruleData = approval?.let {
            SlotAdmissionRuleTypedData(
                timestamp = Time.now,
                user = "someone",
                data = ManualApprovalSlotAdmissionRuleData(approval = it, message = null),
            )
        },
    )

    @Test
    fun `Waiting for an approval is pending`() {
        val check = check(approval = null)
        assertEquals(SlotDeploymentCheckState.PENDING, check.state)
        assertEquals("No approval", check.reason)
    }

    @Test
    fun `A rejection has failed`() {
        val check = check(approval = false)
        assertEquals(SlotDeploymentCheckState.FAILED, check.state)
        assertEquals("Rejected", check.reason)
    }

    @Test
    fun `An approval is OK`() {
        assertEquals(SlotDeploymentCheckState.OK, check(approval = true).state)
    }

}
