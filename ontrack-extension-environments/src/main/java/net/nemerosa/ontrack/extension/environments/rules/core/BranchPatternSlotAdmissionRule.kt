package net.nemerosa.ontrack.extension.environments.rules.core

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.FilterHelper
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.ordering.BranchOrderingService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class BranchPatternSlotAdmissionRule(
    private val branchOrderingService: BranchOrderingService,
    private val structureService: StructureService,
) : SlotAdmissionRule<BranchPatternSlotAdmissionRuleConfig, Any> {

    companion object {
        const val ID = "branchPattern"
    }

    override val id: String = ID
    override val name: String = "Branch pattern"

    override val configType: KClass<BranchPatternSlotAdmissionRuleConfig> = BranchPatternSlotAdmissionRuleConfig::class

    override fun parseConfig(jsonRuleConfig: JsonNode) = jsonRuleConfig.parse<BranchPatternSlotAdmissionRuleConfig>()

    override fun isBuildEligible(build: Build, slot: Slot, config: BranchPatternSlotAdmissionRuleConfig): Boolean =
        // `lastBranchOnly` is a deployability criteria, see checkBuildDeployable
        isBranchEligible(build.branch.name, config)

    /**
     * Whether a branch is included by the patterns at all, asked of the branch alone.
     *
     * This is the branch half of [isBuildEligible], split out rather than copied because the
     * delivery map asks the same question of a branch with no build in hand: a pattern which
     * excludes the branch means no build of it can EVER be deployed to the slot, however far it is
     * promoted, and the map draws such a slot as unreachable. Two hand-written readings of one
     * pattern would be free to drift, and a map disagreeing with the deployment is worse than no
     * map.
     *
     * `lastBranchOnly` is deliberately not part of it. It is a fact about the branches which exist
     * right now rather than about this branch, so it belongs to deployability and is applied there.
     */
    fun isBranchEligible(branchName: String, config: BranchPatternSlotAdmissionRuleConfig): Boolean =
        FilterHelper.includes(
            text = branchName,
            includes = config.includes,
            excludes = config.excludes,
        )

    override fun fillEligibilityCriteria(
        slot: Slot,
        config: BranchPatternSlotAdmissionRuleConfig,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {
        if (config.includes.isEmpty()) {
            queries += "0 != 1" // No inclusion, no build
        } else {
            config.includes.forEachIndexed { index, s ->
                queries += "B.NAME ~ :include$index"
                params["include$index"] = s
            }
            config.excludes.forEachIndexed { index, s ->
                queries += "B.NAME !~ :exclude$index"
                params["exclude$index"] = s
            }
        }
        if (deployable && config.lastBranchOnly) {
            // Getting the latest branch on the slot project for the given patterns
            val lastBranch = findLastBranch(slot.project, config)
            // We want the branch to be the latest one
            queries += "B.ID = :lastBranchId"
            params["lastBranchId"] = lastBranch?.id()
        }
    }

    private fun findLastBranch(
        project: Project,
        ruleConfig: BranchPatternSlotAdmissionRuleConfig
    ): Branch? {
        val ordering = branchOrderingService.getSemVerBranchOrdering(
            branchNamePolicy = BranchNamePolicy.NAME_ONLY,
        )
        val branches = structureService.filterBranchesForProject(
            project = project,
            filter = BranchFilter(
                name = ruleConfig.includes.takeIf { it.isNotEmpty() }?.joinToString("|"),
                excludes = ruleConfig.excludes.takeIf { it.isNotEmpty() }?.joinToString("|"),
                count = 100, // Arbitrary number
            )
        ).sortedWith(ordering)
        return branches.firstOrNull()
    }

    override fun isBuildDeployable(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        ruleConfig: BranchPatternSlotAdmissionRuleConfig,
        ruleData: SlotAdmissionRuleTypedData<Any>?
    ): SlotDeploymentCheck = checkBuildDeployable(pipeline.build, pipeline.slot, ruleConfig)

    /**
     * Build deployable if its branch is included by the patterns and, with `lastBranchOnly`, if it
     * is the last of the included branches - the same last branch as [fillEligibilityCriteria].
     */
    override fun checkBuildDeployable(
        build: Build,
        slot: Slot,
        config: BranchPatternSlotAdmissionRuleConfig
    ): SlotDeploymentCheck {
        val check = isBuildEligible(build = build, slot = slot, config = config) &&
                (!config.lastBranchOnly || findLastBranch(slot.project, config)?.id == build.branch.id)
        return SlotDeploymentCheck.check(
            check = check,
            ok = "Build branch is valid",
            nok = "Build branch is not valid",
        )
    }

    override fun parseData(node: JsonNode): Any = ""

    override fun checkConfig(ruleConfig: JsonNode) {
        ruleConfig.parseOrNull<BranchPatternSlotAdmissionRuleConfig>()
            ?: throw SlotAdmissionRuleConfigException("Cannot parse the rule config")
    }
}