package net.nemerosa.ontrack.model.structure

/**
 * Told what a deletion takes with it on builds other than the deleted entity: the validation runs
 * of a deleted validation stamp, the promotion runs of a deleted promotion level, and the links
 * other builds hold to a deleted build — and told of a validation run deleted on its own, which
 * takes with it what is attached to it.
 *
 * Called just before the deletion, in its transaction, while what it takes can still be read: what
 * a listener writes is committed or rolled back with the deletion, and a listener which fails
 * fails the deletion.
 *
 * The deletions of a branch or of a project are not reported — they take their builds with them.
 */
interface CascadeDeletionListener {

    /**
     * Whether this listener is to be told at all: a deletion lists what it takes only when one of
     * the listeners is listening, so that the listing costs nothing otherwise.
     */
    val isListening: Boolean

    /**
     * Before the deletion of a validation stamp.
     *
     * @param validationStamp Validation stamp about to be deleted
     * @param runs Its validation runs, ordered by build and run ID
     */
    fun beforeValidationStampDeletion(validationStamp: ValidationStamp, runs: List<CascadedValidationRun>)

    /**
     * Before the deletion of a validation run on its own — before the event of its deletion is
     * posted.
     *
     * @param validationRun Validation run about to be deleted
     */
    fun beforeValidationRunDeletion(validationRun: ValidationRun)

    /**
     * Before the deletion of a promotion level.
     *
     * @param promotionLevel Promotion level about to be deleted
     * @param runs Its promotion runs, ordered by build and run ID
     */
    fun beforePromotionLevelDeletion(promotionLevel: PromotionLevel, runs: List<CascadedPromotionRun>)

    /**
     * Before the deletion of a build.
     *
     * @param build Build about to be deleted
     * @param links Links other builds hold to it, ordered by source build and qualifier
     */
    fun beforeBuildDeletion(build: Build, links: List<CascadedBuildLink>)
}

/**
 * A validation run deleted with its validation stamp.
 *
 * @property build Build of the run
 * @property id ID of the run
 * @property runOrder Order of the run among the runs of its build for its validation stamp, from 1
 * @property status ID of the last status of the run
 */
data class CascadedValidationRun(
    val build: Build,
    val id: Int,
    val runOrder: Int,
    val status: String,
)

/**
 * A promotion run deleted with its promotion level.
 *
 * @property build Promoted build
 * @property id ID of the run
 */
data class CascadedPromotionRun(
    val build: Build,
    val id: Int,
)

/**
 * A link to a build, deleted with it.
 *
 * @property build Build holding the link
 * @property qualifier Qualifier of the link, empty for the default one
 */
data class CascadedBuildLink(
    val build: Build,
    val qualifier: String,
)
