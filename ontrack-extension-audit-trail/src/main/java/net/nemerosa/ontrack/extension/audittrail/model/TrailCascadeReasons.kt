package net.nemerosa.ontrack.extension.audittrail.model

/**
 * Reasons of the entries a deletion writes on the builds it reaches beyond the deleted entity —
 * the `reason` of their payloads, `cascade/<cause>`. An entry written for a change of the build
 * itself has no reason.
 */
object TrailCascadeReasons {

    /**
     * [validation.deleted][TrailEntryTypes.VALIDATION_DELETED] of a run deleted with its validation
     * stamp.
     */
    const val VALIDATION_STAMP_DELETED = "cascade/validation-stamp-deleted"

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
