package net.nemerosa.ontrack.extension.audittrail.model

/**
 * Types of the entries of a trail — the closed list of schema version 1.
 *
 * Every type but [TRAIL_OPENED] is written from an event of the change it records (see
 * `AuditTrailEventListener`). The payloads hold stable identifiers and small values, never
 * entity dumps; a value which is not set is absent from its payload. Times are written as hashed
 * (`TrailHashFormatV1.formatTime`). What a caller supplied — a back-dated time, a user name — is
 * kept as `claimed: {time, user}`, the entry's own time and actor being the server's.
 *
 * The `evidence.*` types come with the evidence service.
 *
 * A deletion which reaches builds beyond the deleted entity — a validation stamp, a promotion level,
 * a build other builds link to — writes on each of them `validation.deleted`, `promotion.removed`
 * or `link.removed`, with a `reason: cascade/<cause>` ([TrailCascadeReasons]). The deletions of a
 * branch or a project write nothing: the trails go with the builds.
 */
object TrailEntryTypes {

    /**
     * First entry of the trail of a build which predates it — the build was created before the
     * feature, or while the licence was off. Its payload names the build, gives its creation time,
     * and marks the trail as partial.
     */
    const val TRAIL_OPENED = "trail.opened"

    /**
     * Creation of a build: the first entry of a complete trail.
     *
     * `{build: {id, project, branch, name}, description, claimed}`, `claimed` being the build's
     * signature.
     */
    const val BUILD_CREATED = "build.created"

    /**
     * Edition of a build: `{old, new}`, each `{name, description, creation, creator}` — renames and
     * back-dating are visible.
     */
    const val BUILD_UPDATED = "build.updated"

    /**
     * Property set on a build: `{propertyType, value}` — the FQCN of the property type and the
     * canonical form of its stored value.
     */
    const val PROPERTY_SET = "property.set"

    /**
     * Property deleted from a build: `{propertyType}`.
     */
    const val PROPERTY_DELETED = "property.deleted"

    /**
     * Link added from the build to another one: `{target: {id, project, branch, name}, qualifier}`.
     */
    const val LINK_ADDED = "link.added"

    /**
     * Link removed from the build to another one: as [LINK_ADDED] — plus
     * `reason: cascade/target-build-deleted` when the link went with its target build.
     */
    const val LINK_REMOVED = "link.removed"

    /**
     * Validation of the build: `{validationStamp: {id, name}, validationRun: {id, order}, status,
     * data: {type, sha256}, claimed}` — the SHA-256 of the canonical data, not the data, and the
     * signature of the run as claimed.
     */
    const val VALIDATION_RUN = "validation.run"

    /**
     * New status of a validation run: `{validationStamp, validationRun, status, description,
     * claimed}`.
     */
    const val VALIDATION_STATUS = "validation.status"

    /**
     * Comment of a validation run status edited: `{validationStamp, validationRun,
     * validationRunStatusId, comment}`.
     */
    const val VALIDATION_COMMENT = "validation.comment"

    /**
     * Data of a validation run replaced: `{validationStamp, validationRun, data: {type, sha256}}`,
     * without `data` when it was removed.
     */
    const val VALIDATION_DATA = "validation.data"

    /**
     * Validation run deleted: `{validationStamp, validationRun, status}`, its last status — plus
     * `reason: cascade/validation-stamp-deleted` when the run went with its validation stamp.
     */
    const val VALIDATION_DELETED = "validation.deleted"

    /**
     * Promotion of the build: `{promotionLevel: {id, name}, promotionRun: {id}, description,
     * claimed}`.
     */
    const val PROMOTION_ADDED = "promotion.added"

    /**
     * Promotion run deleted: `{promotionLevel, promotionRun}` — plus
     * `reason: cascade/promotion-level-deleted` when the run went with its promotion level.
     */
    const val PROMOTION_REMOVED = "promotion.removed"

    /**
     * Run info set on the build or on one of its validation runs: `{runnable, runInfo: {sourceType,
     * sourceUri, triggerType, triggerData, runTime}}`, `runnable` being `{type: build}` or
     * `{type: validation_run, validationStamp, validationRun}`.
     */
    const val RUN_INFO_SET = "runinfo.set"

    /**
     * Run info deleted from the build or from one of its validation runs: `{runnable}`.
     */
    const val RUN_INFO_DELETED = "runinfo.deleted"

    /**
     * Deployment of the build started — a pipeline created in a slot: `{deployment, message,
     * claimed}`, `deployment` being `{id, number, environment, slot: {id, qualifier}}` and
     * `claimed` the time and user of the change of status.
     */
    const val DEPLOYMENT_CREATED = "deployment.created"

    /**
     * Deployment cancelled: as [DEPLOYMENT_CREATED].
     */
    const val DEPLOYMENT_CANCELLED = "deployment.cancelled"

    /**
     * Deployment running: as [DEPLOYMENT_CREATED].
     */
    const val DEPLOYMENT_RUNNING = "deployment.running"

    /**
     * Deployment done: as [DEPLOYMENT_CREATED].
     */
    const val DEPLOYMENT_DONE = "deployment.done"

    /**
     * Deployment failed: as [DEPLOYMENT_CREATED].
     */
    const val DEPLOYMENT_FAILED = "deployment.failed"

    /**
     * Data set for an admission rule of a deployment: `{deployment, rule: {id, name, ruleId},
     * data: {sha256}, claimed}`.
     */
    const val DEPLOYMENT_RULE_DATA = "deployment.rule-data"

    /**
     * Admission rule of a deployment overridden: `{deployment, rule, message, claimed}`.
     */
    const val DEPLOYMENT_RULE_OVERRIDDEN = "deployment.rule-overridden"

    /**
     * Result of a slot workflow overridden for a deployment: `{deployment, slotWorkflow: {id,
     * instanceId, workflow}, message}`.
     */
    const val DEPLOYMENT_WORKFLOW_OVERRIDDEN = "deployment.workflow-overridden"

    /**
     * Deployment deleted: `{deployment, status}`, its status when it was deleted.
     */
    const val DEPLOYMENT_DELETED = "deployment.deleted"
}
