package net.nemerosa.ontrack.extension.environments.deliverymap

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.rules.core.BranchPatternSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.rules.core.EnvironmentSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.rules.core.PromotionSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.deliverymap.*
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

/**
 * Puts the project's slots on the delivery map of a branch, and draws the *requires* edges its
 * admission rules ask for.
 *
 * This is the map's third checkpoint kind and the only one the core has never heard of. It reaches
 * the map through the plain `List<DeliveryMapContributor>` injection, so a Yontrack without the
 * environments extension - or without a licence for it - simply has one contributor fewer and a map
 * of promotion levels and validation stamps.
 *
 * *Every* slot of the project is drawn, including the ones no admission rule connects to anything
 * and the ones this branch can never reach. A slot is something a build has to pass through on its
 * way to an environment whether or not the configuration says how, and the map's subject is exactly
 * that journey.
 *
 * Slot to slot edges come **only** from [EnvironmentSlotAdmissionRule]. There is no fallback to
 * environment ordering, which is what the project slot graph uses: ordering says which environment
 * comes first, never that one deployment requires another. Where nobody configured the rule there is
 * no edge, and the slots hang off their promotion checkpoints unconnected to each other. That is
 * sparser than the chain the project slot graph draws, and it is the reading which surfaces the
 * misconfiguration rather than inventing a dependency nobody declared.
 *
 * An admission rule naming something which does not exist - a promotion this branch does not have,
 * an environment this project has no slot in - draws an *unresolved* checkpoint carrying the name it
 * asked for (#1705). Both rules reference their target by name inside an opaque JSONB column, with
 * no referential integrity behind them, so this is the only place the mistake ever becomes visible
 * before somebody tries to deploy.
 */
@Component
class SlotDeliveryMapContributor(
    private val slotService: SlotService,
    private val structureService: StructureService,
    private val promotionSlotAdmissionRule: PromotionSlotAdmissionRule,
    private val environmentSlotAdmissionRule: EnvironmentSlotAdmissionRule,
    private val branchPatternSlotAdmissionRule: BranchPatternSlotAdmissionRule,
) : DeliveryMapContributor {

    override fun contribute(branch: Branch): DeliveryMapContribution {
        // `findSlotsByProject` already leaves out the slots the current user may not see, which is
        // what a contributor owes the map: no placeholder for a permission denial, or an unresolved
        // checkpoint would come to read as "probably just permissions".
        val slots = slotService.findSlotsByProject(branch.project)
            .sortedBy { it.environment.order }
        if (slots.isEmpty()) return DeliveryMapContribution.EMPTY

        val checkpoints = mutableListOf<DeliveryMapCheckpoint>()
        val edges = mutableListOf<DeliveryMapEdge>()
        // Keyed by id: several rules, on several slots, routinely ask for the same missing thing, and
        // one missing thing is one checkpoint. Insertion-ordered so that the map's inputs keep the
        // order the slots were read in.
        val unresolved = LinkedHashMap<String, DeliveryMapCheckpoint>()

        // Draws the edge a rule asks for, and - when the rule named something which matches nothing -
        // the checkpoint it runs from.
        fun requires(source: RuleSource, target: String) {
            source.unresolved?.let { unresolved.putIfAbsent(it.id, it) }
            edges += DeliveryMapEdge.of(
                kind = DeliveryMapEdgeKind.REQUIRES,
                source = source.id,
                target = target,
            )
        }

        slots.forEach { slot ->
            val target = SlotDeliveryMapCheckpoints.slot(slot.id)
            var unreachable = false

            slotService.getAdmissionRuleConfigs(slot).forEach { config ->
                // A stored rule configuration which no longer parses costs its own rule and nothing
                // else. Letting it out would cost every slot of the project, since the map isolates
                // a failing contributor as a whole and this contributor draws them all.
                //
                // Only the rule reading is guarded, and the queries below deliberately are not. A
                // configuration whose shape has drifted is a fault in one row; a query that throws
                // is a fault in the instance, and swallowing that one would draw a slot saying
                // "never deployed" when the truth is that nobody could look.
                try {
                    when (config.ruleId) {
                        PromotionSlotAdmissionRule.ID ->
                            requires(promotionSource(branch, config), target)

                        EnvironmentSlotAdmissionRule.ID ->
                            requires(environmentSource(slots, config), target)

                        BranchPatternSlotAdmissionRule.ID ->
                            if (excludesBranch(branch, config)) unreachable = true

                        // Every other rule - a manual approval, say - constrains a build rather than
                        // a branch, so it says nothing about what this branch can reach and draws
                        // nothing here.
                    }
                } catch (ex: Exception) {
                    logger.error(
                        "Delivery map cannot read the admission rule ${config.ruleId} of slot ${slot.fullName()}",
                        ex
                    )
                }
            }

            checkpoints += checkpoint(branch, slot, unreachable)
        }

        return DeliveryMapContribution(
            // The unresolved ones last: they are not slots, and reading the slots of the project as
            // one run is what the layout is given.
            checkpoints = checkpoints + unresolved.values,
            edges = edges,
        )
    }

    /**
     * Where the edge of one admission rule runs from.
     *
     * @property id Id of the checkpoint the edge runs from, resolved or not
     * @property unresolved The checkpoint to contribute, when the rule named something matching
     * nothing. Null when the rule resolved, because the checkpoint it names is then a real one,
     * contributed by whoever owns it - the core for a promotion level, this contributor for a slot.
     */
    private data class RuleSource(
        val id: String,
        val unresolved: DeliveryMapCheckpoint? = null,
    ) {
        companion object {
            fun unresolved(reference: String, name: String): RuleSource {
                val checkpoint = DeliveryMapCheckpoint.unresolved(reference, name)
                return RuleSource(id = checkpoint.id, unresolved = checkpoint)
            }
        }
    }

    /**
     * The promotion level is resolved **per branch**, by name. The same slot configuration therefore
     * yields a different edge on every branch, which is why slots belong on a branch view at all.
     *
     * A name matching no promotion level of this branch is drawn as an unresolved checkpoint carrying
     * that name (#1705). The rule config is read directly because that checkpoint needs the name the
     * rule asked for, and only the config carries it.
     *
     * There is no permission ambiguity to worry about here: promotion levels belong to the branch's
     * own project, and the map already refused to be assembled at all without `ProjectView` on it.
     */
    private fun promotionSource(branch: Branch, config: SlotAdmissionRuleConfig): RuleSource {
        val ruleConfig = promotionSlotAdmissionRule.parseConfig(config.ruleConfig)
        val promotionLevel = structureService.findPromotionLevelByName(
            branch.project.name,
            branch.name,
            ruleConfig.promotion,
        ).getOrNull() ?: return RuleSource.unresolved(
            DeliveryMapCheckpointTypes.PROMOTION_LEVEL,
            ruleConfig.promotion,
        )
        return RuleSource(DeliveryMapCheckpointTypes.promotionLevel(promotionLevel.id))
    }

    /**
     * The rule names an environment and a qualifier; the slot it means is the one of *this* project
     * in that environment. Looking it up among the slots already fetched is both cheaper than asking
     * again and what keeps the answer honest: a rule naming a slot which does not exist is a broken
     * configuration and gets an unresolved checkpoint, and that must never be confused with a slot
     * hidden by permissions.
     *
     * The two cannot be confused, because slot visibility is decided per *project* and never per
     * slot - `EnvironmentList` globally, then `ProjectView` and `SlotView` on the slot's project. All
     * the slots looked at here belong to the branch's project, so the user either sees all of them or
     * none, and the "none" case has already returned above with an empty contribution.
     */
    private fun environmentSource(slots: List<Slot>, config: SlotAdmissionRuleConfig): RuleSource {
        val ruleConfig = environmentSlotAdmissionRule.parseConfig(config.ruleConfig)
        val source = slots.firstOrNull {
            it.environment.name == ruleConfig.environmentName && it.qualifier == ruleConfig.qualifier
        } ?: return RuleSource.unresolved(
            SlotDeliveryMapCheckpoints.SLOT,
            // Qualifier and all: a rule naming `staging [demo]` where only `staging` exists is
            // exactly the mistake worth seeing, and a checkpoint labelled `staging` would hide it.
            //
            // The id #1707 keeps a position by is therefore built from the label rather than from
            // the two fields separately. A rule whose `environmentName` is literally `staging [demo]`
            // then shares a checkpoint with one naming `staging` qualified `demo` - and can, because
            // nothing validates the field, which is this issue's whole subject. Both are unresolved
            // and say the same thing, so the collision costs nothing worth the extra id shape.
            SlotDeliveryMapCheckpoints.label(ruleConfig.environmentName, ruleConfig.qualifier),
        )
        return RuleSource(SlotDeliveryMapCheckpoints.slot(source.id))
    }

    /**
     * Whether a branch pattern rule shuts this branch out of the slot for good.
     *
     * The question is the rule's own, never a copy of it: a second implementation of "does this
     * pattern include this branch" would be free to drift from the one the deployment actually runs,
     * and a map disagreeing with the deployment is worse than no map.
     */
    private fun excludesBranch(branch: Branch, config: SlotAdmissionRuleConfig): Boolean {
        val ruleConfig = branchPatternSlotAdmissionRule.parseConfig(config.ruleConfig)
        return !branchPatternSlotAdmissionRule.isBranchEligible(branch.name, ruleConfig)
    }

    private fun checkpoint(branch: Branch, slot: Slot, unreachable: Boolean): DeliveryMapCheckpoint {
        // The most recently DEPLOYED pipeline, ordered by pipeline number within the slot. The
        // promotion and validation checkpoints order by build id instead, meaning highest build.
        // The two usually agree and disagree after an older build is redeployed; the mismatch is
        // accepted rather than fixed, because "what is in there now" is the question a slot answers.
        val pipeline = if (unreachable) null else slotService.getLastDeployedPipeline(slot)
        val deployedBranch = pipeline?.build?.branch
        return DeliveryMapCheckpoint(
            id = SlotDeliveryMapCheckpoints.slot(slot.id),
            type = SlotDeliveryMapCheckpoints.SLOT,
            name = SlotDeliveryMapCheckpoints.label(slot.environment.name, slot.qualifier),
            description = slot.description,
            data = SlotCheckpointData(
                slotId = slot.id,
                unreachable = unreachable,
                otherBranch = deployedBranch?.name?.takeIf { deployedBranch.id != branch.id },
            ).asJson(),
            // No status: a slot is arrived at by a deployment which is done, and being deployed is
            // the whole outcome.
            arrival = pipeline?.let {
                DeliveryMapArrival(
                    build = it.build,
                    time = it.end ?: it.start,
                )
            },
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SlotDeliveryMapContributor::class.java)
    }
}
