package net.nemerosa.ontrack.extension.environments.readiness

import net.nemerosa.ontrack.extension.api.SlotReadinessExtension
import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.rules.SlotAdmissionRuleRegistry
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.readiness.Readiness
import net.nemerosa.ontrack.model.readiness.ReadinessInputException
import net.nemerosa.ontrack.model.readiness.ReadinessItem
import net.nemerosa.ontrack.model.readiness.ReadinessKind
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Component

/**
 * Readiness of a build for a slot, read from its [eligibility][SlotService.getEligibleSlotsForBuild]:
 *
 * - every admission rule which makes the build not eligible, or not deployable now, is an
 *   [admission rule][ReadinessKind.ADMISSION_RULE] item, with its reason;
 * - every rule which can only be decided on a deployment, like a manual approval, is checked on the
 *   active deployment of the build in the slot, when there is one, and is missing otherwise - a
 *   manual approval as a [manual][ReadinessKind.MANUAL] item.
 */
@Component
class SlotReadinessExtensionImpl(
    extensionFeature: EnvironmentsExtensionFeature,
    private val slotService: SlotService,
    private val slotAdmissionRuleRegistry: SlotAdmissionRuleRegistry,
) : AbstractExtension(extensionFeature), SlotReadinessExtension {

    override fun getSlotReadiness(build: Build, slotId: String): Readiness {
        // Unknown slot and slot of another project alike: not a target for this build
        val eligibleSlot = slotService.getEligibleSlotsForBuild(build).firstOrNull { it.slot.id == slotId }
            ?: throw ReadinessInputException(
                "There is no slot with ID $slotId in the project ${build.project.name} of the build."
            )
        val slot = eligibleSlot.slot
        // Not eligible
        val nonEligible = eligibleSlot.nonEligibleRules.map { config ->
            ReadinessItem(
                kind = ReadinessKind.ADMISSION_RULE,
                name = config.name,
                message = "The build is not eligible for this slot: the ${ruleName(config)} rule refuses it" +
                        (config.description?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                        ".",
            )
        }
        // Not deployable
        val nonDeployable = eligibleSlot.nonDeployableRules.map { check ->
            ReadinessItem(
                kind = ReadinessKind.ADMISSION_RULE,
                name = check.rule.name,
                message = check.reason?.takeIf { it.isNotBlank() }
                    ?: "The ${ruleName(check.rule)} rule does not allow the build to be deployed yet.",
            )
        }
        // Decided on the deployment only
        val pipelineOnly = if (eligibleSlot.pipelineOnlyRules.isNotEmpty()) {
            val pipeline = slotService.findPipelines(
                slot = slot,
                size = 1,
                buildId = build.id(),
                done = false,
            ).pageItems.firstOrNull()
            eligibleSlot.pipelineOnlyRules.mapNotNull { config ->
                pipelineOnlyItem(pipeline, config)
            }
        } else {
            emptyList()
        }
        return Readiness.of(
            (nonEligible + nonDeployable + pipelineOnly).sortedBy { it.kind.ordinal }
        )
    }

    private fun pipelineOnlyItem(pipeline: SlotPipeline?, config: SlotAdmissionRuleConfig): ReadinessItem? {
        val manual = config.ruleId == ManualApprovalSlotAdmissionRule.ID
        val kind = if (manual) ReadinessKind.MANUAL else ReadinessKind.ADMISSION_RULE
        return if (pipeline == null) {
            ReadinessItem(
                kind = kind,
                name = config.name,
                message = if (manual) {
                    "A person must approve the deployment: the approval is given on a deployment of the build, " +
                            "once it is started."
                } else {
                    "The ${ruleName(config)} rule is decided on a deployment of the build, once it is started."
                },
            )
        } else {
            val check = slotService.getAdmissionRuleCheck(pipeline, config)
            if (check.ok) {
                null
            } else {
                ReadinessItem(
                    kind = kind,
                    name = config.name,
                    message = if (manual) {
                        "A person must approve deployment #${pipeline.number}: ${check.reason ?: "no approval"}."
                    } else {
                        "The ${ruleName(config)} rule refuses deployment #${pipeline.number}" +
                                (check.reason?.let { ": $it" } ?: "") + "."
                    },
                )
            }
        }
    }

    private fun ruleName(config: SlotAdmissionRuleConfig): String =
        slotAdmissionRuleRegistry.rules.firstOrNull { it.id == config.ruleId }?.name ?: config.ruleId
}
