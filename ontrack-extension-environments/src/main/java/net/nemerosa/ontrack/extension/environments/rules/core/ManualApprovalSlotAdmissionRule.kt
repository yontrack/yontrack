package net.nemerosa.ontrack.extension.environments.rules.core

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class ManualApprovalSlotAdmissionRule(
    private val securityService: SecurityService,
) : SlotAdmissionRule<ManualApprovalSlotAdmissionRuleConfig, ManualApprovalSlotAdmissionRuleData> {

    companion object {
        const val ID = "manual"
    }

    override val id: String = ID
    override val name: String = "Manual approval"

    override val configType: KClass<ManualApprovalSlotAdmissionRuleConfig> =
        ManualApprovalSlotAdmissionRuleConfig::class

    override fun parseConfig(jsonRuleConfig: JsonNode): ManualApprovalSlotAdmissionRuleConfig = jsonRuleConfig.parse()

    /**
     * Any build is eligible
     */
    override fun fillEligibilityCriteria(
        slot: Slot,
        config: ManualApprovalSlotAdmissionRuleConfig,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {
    }

    /**
     * All builds are eligible
     */
    override fun isBuildEligible(build: Build, slot: Slot, config: ManualApprovalSlotAdmissionRuleConfig): Boolean =
        true

    /**
     * The approval is given on the pipeline: nothing can be said of the build alone.
     */
    override fun checkBuildDeployable(
        build: Build,
        slot: Slot,
        config: ManualApprovalSlotAdmissionRuleConfig
    ): SlotDeploymentCheck? = null

    /**
     * A build is deployable if the user has approved the rule in the pipeline.
     *
     * Only some users or groups are eligible.
     */
    override fun isBuildDeployable(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        ruleConfig: ManualApprovalSlotAdmissionRuleConfig,
        ruleData: SlotAdmissionRuleTypedData<ManualApprovalSlotAdmissionRuleData>?
    ): SlotDeploymentCheck {
        return if (ruleData?.data != null) {
            if (!ruleData.data.approval) {
                SlotDeploymentCheck.nok("Rejected")
            } else {
                SlotDeploymentCheck.ok()
            }
        } else {
            SlotDeploymentCheck.nok("No approval")
        }
    }

    override fun checkConfig(ruleConfig: JsonNode) {
        ruleConfig.parseOrNull<ManualApprovalSlotAdmissionRuleConfig>()
            ?: throw SlotAdmissionRuleConfigException("Cannot parse the rule config")
    }

    override fun isDataNeeded(ruleConfig: ManualApprovalSlotAdmissionRuleConfig): Boolean = true

    override fun parseData(node: JsonNode): ManualApprovalSlotAdmissionRuleData =
        node.parse()

    override fun checkData(ruleConfig: JsonNode, data: JsonNode) {
        val c = parseConfig(ruleConfig)
        val d = parseData(data)
        // Controls of the user
        val user = securityService.currentSignature.user.name
        if (c.users.isNotEmpty()) {
            if (user !in c.users) {
                throw ManualApprovalSlotAdmissionRuleException("User not authorized to approve")
            }
        }
        // Controls of the group
        if (c.groups.isNotEmpty()) {
            val groups = securityService.currentUser?.groups?.map { it.group.name } ?: emptyList()
            if (c.groups.intersect(groups.toSet()).isEmpty()) {
                throw ManualApprovalSlotAdmissionRuleException("Group not authorized to approve")
            }
        }
        // OK
    }
}