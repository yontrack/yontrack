package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.extension.api.AgentPolicySlotExtension
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class AgentPolicyServiceImpl(
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val extensionManager: ExtensionManager,
    /**
     * Without any, no promotion level admits agents.
     */
    private val promotionLevelAgentAdmissions: ObjectProvider<PromotionLevelAgentAdmission>,
) : AgentPolicyService {

    override fun getAgentPolicy(project: String): AgentProjectPolicy? {
        val agent = securityService.currentAgent ?: return null
        val p = structureService.findProjectByName(project).getOrNull()
            ?: throw ProjectNotFoundException(project)
        return AgentProjectPolicy(
            project = p,
            owner = getOwnerName(agent),
            canRecordEvidence = securityService.isProjectFunctionGranted(p, BuildCreate::class.java) &&
                    securityService.isProjectFunctionGranted(p, ValidationRunCreate::class.java),
        )
    }

    override fun getAdmittedPromotionLevels(project: Project, branch: String?): List<AgentPolicyPromotionLevel> {
        if (securityService.currentAgent == null ||
            !securityService.isProjectFunctionGranted(project, PromotionRunCreate::class.java)
        ) {
            return emptyList()
        }
        return structureService.getBranchesForProject(project.id)
            .filter { !it.isDisabled && (branch.isNullOrBlank() || it.name == branch) }
            .flatMap { b ->
                structureService.getPromotionLevelListForBranch(b.id)
                    .filter { isAgentsAdmitted(it) }
                    .map { pl ->
                        AgentPolicyPromotionLevel(
                            id = pl.id(),
                            branch = b.name,
                            name = pl.name,
                        )
                    }
            }
    }

    override fun getAdmittedSlots(project: Project): List<AgentPolicySlot> =
        if (securityService.currentAgent == null) {
            emptyList()
        } else {
            extensionManager.getExtensions(AgentPolicySlotExtension::class.java)
                .flatMap { it.getAdmittedSlots(project) }
        }

    override fun isAgentsAdmitted(promotionLevel: PromotionLevel): Boolean =
        promotionLevelAgentAdmissions.any { it.isAgentsAdmitted(promotionLevel) }

    override fun getOwnerName(agent: Account): String =
        agent.owner?.fullName ?: "a person"
}
