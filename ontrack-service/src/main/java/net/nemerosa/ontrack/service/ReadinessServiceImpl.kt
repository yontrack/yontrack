package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.api.PromotionLevelReadinessExtension
import net.nemerosa.ontrack.extension.api.SlotReadinessExtension
import net.nemerosa.ontrack.model.readiness.*
import net.nemerosa.ontrack.model.security.AgentPolicyService
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.currentAgent
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRunCheckService
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReadinessServiceImpl(
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val promotionRunCheckService: PromotionRunCheckService,
    private val extensionManager: ExtensionManager,
    private val agentPolicyService: AgentPolicyService,
) : ReadinessService {

    override fun getReadiness(build: Build, promotionLevel: String?, slotId: String?): Readiness {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return when {
            !promotionLevel.isNullOrBlank() && slotId.isNullOrBlank() -> {
                val pl = structureService.findPromotionLevelByName(
                    build.project.name,
                    build.branch.name,
                    promotionLevel
                ).orElse(null)
                    ?: throw ReadinessInputException(
                        "There is no promotion level named $promotionLevel on the branch ${build.branch.name} " +
                                "of the build."
                    )
                getPromotionLevelReadiness(build, pl)
            }

            promotionLevel.isNullOrBlank() && !slotId.isNullOrBlank() -> getSlotReadiness(build, slotId)

            else -> throw ReadinessInputException(
                "Exactly one of promotionLevel and slotId must be given for the readiness of a build."
            )
        }
    }

    override fun getPromotionLevelReadiness(build: Build, promotionLevel: PromotionLevel): Readiness {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        if (promotionLevel.branch.id != build.branch.id) {
            throw ReadinessInputException(
                "The promotion level ${promotionLevel.name} is not on the branch ${build.branch.name} of the build."
            )
        }
        // Already promoted: nothing is missing
        if (structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel).isNotEmpty()) {
            return Readiness.READY
        }
        // Auto promotion conditions, or manual promotion
        val conditions = extensionManager.getExtensions(PromotionLevelReadinessExtension::class.java)
            .flatMap { it.getPromotionLevelMissing(build, promotionLevel) }
        // Promotion checks, all of them
        val checks = promotionRunCheckService.explainPromotionRunCreation(build, promotionLevel).map {
            ReadinessItem(
                kind = ReadinessKind.CHECK,
                name = it.check,
                message = it.reason,
            )
        }
        // The agent policy, for an agent only
        val agentPolicy = listOfNotNull(agentPolicyItem(promotionLevel))
        // What needs a person comes last
        return Readiness.of(
            (conditions + checks + agentPolicy).sortedBy { it.kind.ordinal }
        )
    }

    /**
     * An agent reading the readiness of a level which does not admit agents learns it here, with
     * everything else which is missing, rather than by being refused.
     */
    private fun agentPolicyItem(promotionLevel: PromotionLevel): ReadinessItem? {
        val agent = securityService.currentAgent ?: return null
        return if (agentPolicyService.isAgentsAdmitted(promotionLevel)) {
            null
        } else {
            ReadinessItem(
                kind = ReadinessKind.AGENT_POLICY,
                name = promotionLevel.name,
                message = "agents are not admitted on ${promotionLevel.name}; ask ${agentPolicyService.getOwnerName(agent)}",
            )
        }
    }

    private fun getSlotReadiness(build: Build, slotId: String): Readiness {
        val extension = extensionManager.getExtensions(SlotReadinessExtension::class.java).firstOrNull()
            ?: throw ReadinessInputException("Slots are not available: no readiness can be computed for a slot.")
        return extension.getSlotReadiness(build, slotId)
    }
}
