package net.nemerosa.ontrack.extension.audittrail.model

/**
 * Reasons of the entries a deletion writes on the builds it reaches beyond the deleted entity, and
 * for the evidence going with a deleted validation run — the `reason` of their payloads,
 * `cascade/<cause>`. An entry written for a change of the build itself has no reason.
 */
object TrailCascadeReasons {

    /**
     * [validation.deleted][TrailEntryTypes.VALIDATION_DELETED] of a run deleted with its validation
     * stamp, and [evidence.deleted][TrailEntryTypes.EVIDENCE_DELETED] of the evidence of the run.
     */
    const val VALIDATION_STAMP_DELETED = "cascade/validation-stamp-deleted"

    /**
     * [evidence.deleted][TrailEntryTypes.EVIDENCE_DELETED] of an evidence gone with its validation
     * run, deleted on its own. The evidence of a run deleted with its validation stamp has
     * [VALIDATION_STAMP_DELETED].
     */
    const val VALIDATION_RUN_DELETED = "cascade/validation-run-deleted"

    /**
     * [promotion.removed][TrailEntryTypes.PROMOTION_REMOVED] of a run deleted with its promotion
     * level.
     */
    const val PROMOTION_LEVEL_DELETED = "cascade/promotion-level-deleted"

    /**
     * [link.removed][TrailEntryTypes.LINK_REMOVED] of a link deleted with the build it targeted.
     */
    const val TARGET_BUILD_DELETED = "cascade/target-build-deleted"
}
