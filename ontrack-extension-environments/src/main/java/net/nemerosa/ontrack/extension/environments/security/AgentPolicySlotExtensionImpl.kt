package net.nemerosa.ontrack.extension.environments.security

import net.nemerosa.ontrack.extension.api.AgentPolicySlotExtension
import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.isSlotAccessible
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.security.AgentPolicySlot
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

/**
 * The slots of a project where the current agent may start a deployment pipeline: those which admit
 * agents, where the agent - that is, its owner, narrowed by the agent policy - may create a
 * pipeline. A manual approval rule is flagged, since it will still stop the agent.
 */
@Component
class AgentPolicySlotExtensionImpl(
    extensionFeature: EnvironmentsExtensionFeature,
    private val slotService: SlotService,
    private val securityService: SecurityService,
) : AbstractExtension(extensionFeature), AgentPolicySlotExtension {

    override fun getAdmittedSlots(project: Project): List<AgentPolicySlot> =
        slotService.findSlotsByProject(project)
            .filter { it.agentsAdmitted && securityService.isSlotAccessible<SlotPipelineCreate>(it) }
            .sortedWith(compareBy({ it.environment.order }, { it.environment.name }, { it.qualifier }))
            .map { slot ->
                AgentPolicySlot(
                    id = slot.id,
                    environment = slot.environment.name,
                    qualifier = slot.qualifier,
                    manualApproval = slotService.getAdmissionRuleConfigs(slot).any {
                        it.ruleId == ManualApprovalSlotAdmissionRule.ID
                    },
                )
            }
}
