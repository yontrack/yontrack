package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Single source of truth for the question "are this build's prerequisites for this promotion level
 * satisfied?".
 *
 * Both the auto promotion ([AutoPromotionEventListener]) and the auto revocation
 * ([AutoPromotionRevocationEventListener]) sides must answer this question exactly the same way:
 * two hand-written copies of the rule would drift, and a build could then be promoted and revoked
 * in a loop, or never recover.
 */
@Component
class AutoPromotionPrerequisites(
    private val structureService: StructureService,
    private val promotionRunService: PromotionRunService,
    private val validationRunService: ValidationRunService,
) {

    /**
     * Checks that every validation stamp and every promotion level required by the [property] is
     * satisfied on the [build].
     *
     * The branch's [branchValidationStamps] and [branchPromotionLevels] are passed in because callers
     * typically loop over all the promotion levels of a branch and would otherwise reload them each time.
     *
     * @param excludedPromotionRunId Promotion run to discount when looking at the granted promotions.
     * [StructureService.deletePromotionRun] posts its event *before* deleting the run, so a listener
     * reacting to that event still sees the run in the database. Passing its ID here is what makes the
     * revocation cascade see the state the deletion is about to produce.
     */
    fun areSatisfied(
        build: Build,
        property: AutoPromotionProperty,
        branchPromotionLevels: List<PromotionLevel>,
        branchValidationStamps: List<ValidationStamp>,
        excludedPromotionRunId: Int? = null,
    ): Boolean {
        // Checks the status of each required validation stamp
        val allVSPassed = branchValidationStamps
            // Keeps only the ones selected by the auto promotion property
            .filter { vs -> property.contains(vs) }
            // They must all pass - note that `isValidationRunPassed` looks at the *last* run only
            .all { vs -> validationRunService.isValidationRunPassed(build, vs) }
        // Checks that all the required promotions are granted
        val allPLPassed = branchPromotionLevels
            // Keeps only the ones selected by the auto promotion property
            .filter { pl -> property.contains(pl) }
            // They must all be granted
            .all { pl -> isPromoted(build, pl, excludedPromotionRunId) }
        return allVSPassed && allPLPassed
    }

    /**
     * Checks if the [build] is promoted to the [promotionLevel], discounting the
     * [excludedPromotionRunId] promotion run if any.
     */
    fun isPromoted(build: Build, promotionLevel: PromotionLevel, excludedPromotionRunId: Int? = null): Boolean =
        if (excludedPromotionRunId == null) {
            promotionRunService.isBuildPromoted(build, promotionLevel)
        } else {
            structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)
                .any { it.id() != excludedPromotionRunId }
        }
}
