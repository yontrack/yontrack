package net.nemerosa.ontrack.extension.environments.rules.core

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTypedData
import net.nemerosa.ontrack.extension.environments.SlotDeploymentCheckState
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountAuthenticatedUser
import net.nemerosa.ontrack.model.security.Authorisations
import net.nemerosa.ontrack.model.security.SecurityRole
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private val owner = Account(
        id = ID.of(1),
        fullName = "Damien Coraboeuf",
        email = "damien@example.com",
        role = SecurityRole.USER,
    )

    private val agent = Account.agent(
        slug = "claude-code-damien",
        displayName = "Claude Code",
        owner = owner,
        tool = "Claude Code",
        description = null,
    ).withId(ID.of(2))

    private fun securityServiceFor(account: Account): SecurityService = mockk {
        every { currentUser } returns AccountAuthenticatedUser(
            account = account,
            authorisations = Authorisations.none(),
            groups = emptyList(),
            assignedGroups = emptyList(),
            mappedGroups = emptyList(),
            idpGroups = emptyList(),
        )
        every { currentSignature } returns Signature.of(account.email)
    }

    private val approval = ManualApprovalSlotAdmissionRuleData(approval = true, message = null).asJson()

    @Test
    fun `An agent cannot approve, even when listed in the users`() {
        val rule = ManualApprovalSlotAdmissionRule(securityServiceFor(agent))
        val ex = assertFailsWith<ManualApprovalSlotAdmissionRuleException> {
            rule.checkData(
                ManualApprovalSlotAdmissionRuleConfig(message = "Approval needed", users = listOf(agent.email)).asJson(),
                approval,
            )
        }
        assertEquals("an agent cannot approve; ask Damien Coraboeuf", ex.message)
    }

    @Test
    fun `A person can approve`() {
        val rule = ManualApprovalSlotAdmissionRule(securityServiceFor(owner))
        rule.checkData(
            ManualApprovalSlotAdmissionRuleConfig(message = "Approval needed", users = listOf(owner.email)).asJson(),
            approval,
        )
    }

}
