package net.nemerosa.ontrack.extension.environments.rules.core

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.rules.PromotionRelatedSlotAdmissionRule
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass

@Component
class PromotionSlotAdmissionRule(
    private val structureService: StructureService,
) : SlotAdmissionRule<PromotionSlotAdmissionRuleConfig, Any>,
    PromotionRelatedSlotAdmissionRule<PromotionSlotAdmissionRuleConfig, Any> {

    companion object {
        const val ID = "promotion"
    }

    override val id: String = ID
    override val name: String = "Promotion"

    override val configType: KClass<PromotionSlotAdmissionRuleConfig> = PromotionSlotAdmissionRuleConfig::class

    /**
     * Getting builds where their branch contains the configured promotion.
     *
     * Builds may or may not be promoted
     */
    override fun fillEligibilityCriteria(
        slot: Slot,
        config: PromotionSlotAdmissionRuleConfig,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {
        queries += "PL.NAME = :promotionName"
        params["promotionName"] = config.promotion
        if (deployable) {
            // Promoted to *this* level, not to any level of the branch
            queries += """
                EXISTS (
                    SELECT 1
                    FROM PROMOTION_RUNS PRD
                    WHERE PRD.BUILDID = BD.ID
                    AND PRD.PROMOTIONLEVELID = PL.ID
                )
            """
        }
    }

    override fun parseConfig(jsonRuleConfig: JsonNode): PromotionSlotAdmissionRuleConfig = jsonRuleConfig.parse()

    /**
     * Build eligible if its branch has the promotion required by the rule.
     */
    override fun isBuildEligible(build: Build, slot: Slot, config: PromotionSlotAdmissionRuleConfig): Boolean =
        structureService.getPromotionLevelListForBranch(build.branch.id).any { it.name == config.promotion }

    override fun isBuildDeployable(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        ruleConfig: PromotionSlotAdmissionRuleConfig,
        ruleData: SlotAdmissionRuleTypedData<Any>?
    ): SlotDeploymentCheck = checkBuildDeployable(pipeline.build, pipeline.slot, ruleConfig)

    /**
     * Build deployable if it is promoted to the level required by the rule.
     */
    override fun checkBuildDeployable(
        build: Build,
        slot: Slot,
        config: PromotionSlotAdmissionRuleConfig
    ): SlotDeploymentCheck {
        val pl = structureService.findPromotionLevelByName(
            build.project.name,
            build.branch.name,
            config.promotion
        ).getOrNull() ?: return SlotDeploymentCheck.nok("Promotion not existing")
        return SlotDeploymentCheck.check(
            structureService.getLastPromotionRunForBuildAndPromotionLevel(build, pl).getOrNull() != null,
            "Build promoted",
            "Build not promoted"
        )
    }

    override fun checkConfig(ruleConfig: JsonNode) {
        ruleConfig.parseOrNull<PromotionSlotAdmissionRuleConfig>()
            ?: throw SlotAdmissionRuleConfigException("Cannot parse the rule config")
    }

    override fun parseData(node: JsonNode): Any = ""

    override fun isForPromotionLevel(
        ruleConfig: PromotionSlotAdmissionRuleConfig,
        promotionLevel: PromotionLevel
    ): Boolean = promotionLevel.name == ruleConfig.promotion
}