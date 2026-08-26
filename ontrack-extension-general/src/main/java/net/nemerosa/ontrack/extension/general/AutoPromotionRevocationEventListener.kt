package net.nemerosa.ontrack.extension.general

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.metrics.increment
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Revokes an auto promotion as soon as one of its prerequisites - a required validation stamp or a
 * required promotion - is no longer valid, for the promotion levels which have opted in through
 * [AutoPromotionProperty.autoRevoke].
 *
 * Revocation deletes the promotion run: it is *not* a rollback. Anything the promotion already set in
 * motion - notifications, subscriptions, workflows, auto-versioning PRs - stays done.
 *
 * Kept apart from [AutoPromotionEventListener], which already carries three unrelated jobs. The rule
 * deciding whether the prerequisites hold is shared through [AutoPromotionPrerequisites] so that the
 * promotion and the revocation sides can never drift apart.
 */
@Component
class AutoPromotionRevocationEventListener(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val securityService: SecurityService,
    private val eventPostService: EventPostService,
    private val eventFactory: EventFactory,
    private val autoPromotionPrerequisites: AutoPromotionPrerequisites,
    private val meterRegistry: MeterRegistry,
) : EventListener {

    private val logger: Logger = LoggerFactory.getLogger(AutoPromotionRevocationEventListener::class.java)

    /**
     * Build/promotion level pairs whose revocation is already running further up the call stack, for the
     * current thread.
     *
     * Deleting a run posts `DELETE_PROMOTION_RUN` *before* the run leaves the database, and listeners are
     * called synchronously: without this guard, a promotion level carrying several runs recurses forever -
     * deleting the first run re-enters, which still sees the second one, deletes it, re-enters, still sees
     * the first one, and so on. The guard stops the re-entry for the promotion level being revoked while
     * leaving the cascade to *other* promotion levels intact.
     */
    private val revocationsInProgress = ThreadLocal.withInitial { mutableSetOf<Pair<Int, Int>>() }

    override fun onEvent(event: Event) {
        when {
            // (a) the last run of a required validation stamp flips to a non-passed status, and
            // (b) a *new* run is created for a required stamp with a non-passed status - `isValidationRunPassed`
            //     looks at the last run only, so a failing re-run invalidates the prerequisite even though an
            //     earlier passing run still exists
            event.eventType === EventFactory.NEW_VALIDATION_RUN ||
                    event.eventType === EventFactory.NEW_VALIDATION_RUN_STATUS -> onValidationRunStatus(event)
            // (c) a required promotion's run is deleted - this is also the cascade mechanism
            event.eventType === EventFactory.DELETE_PROMOTION_RUN -> onDeletePromotionRun(event)
        }
    }

    private fun onValidationRunStatus(event: Event) {
        val validationRun = event.getEntity<ValidationRun>(ProjectEntityType.VALIDATION_RUN)
        if (!validationRun.isPassed) {
            val validationStamp = validationRun.validationStamp
            revokeEligiblePromotions(
                event = event,
                excludedPromotionRunId = null,
                // Only the promotion levels which actually require this stamp: something became invalid for
                // them. Re-judging the others would revoke promotions this event says nothing about - a
                // build promoted by hand over a stamp which never ran, for instance.
                requires = { property -> property.contains(validationStamp) },
            )
        }
    }

    private fun onDeletePromotionRun(event: Event) {
        val promotionLevel = event.getEntity<PromotionLevel>(ProjectEntityType.PROMOTION_LEVEL)
        // `StructureServiceImpl.deletePromotionRun` posts this event *before* deleting the run, and
        // `EventPostServiceImpl.post` calls the listeners synchronously: the run is therefore still in the
        // database at this point. Excluding it by ID is what makes the cascade see the state the deletion
        // is about to produce.
        revokeEligiblePromotions(
            event = event,
            excludedPromotionRunId = event.getIntValue("PROMOTION_RUN_ID"),
            requires = { property -> property.contains(promotionLevel) },
        )
    }

    private fun revokeEligiblePromotions(
        event: Event,
        excludedPromotionRunId: Int?,
        requires: (AutoPromotionProperty) -> Boolean,
    ) {
        val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
        val build = event.getEntity<Build>(ProjectEntityType.BUILD)
        // `deletePromotionRun` requires `PromotionRunDelete`, which the user who flipped a validation to
        // FAILED will typically not hold.
        securityService.asAdmin {
            val promotionLevels = structureService.getPromotionLevelListForBranch(branch.id)
            val validationStamps = structureService.getValidationStampListForBranch(branch.id)
            promotionLevels.forEach { promotionLevel ->
                // Listeners run inside the `@Transactional post()` of the event service: letting an exception
                // escape here would roll back the originating validation run status update, failing a CI job
                // because an unrelated promotion could not be revoked. Recording validation results is the
                // primary duty of the system; revocation is a derived convenience.
                try {
                    revokePromotionLevel(
                        build = build,
                        promotionLevel = promotionLevel,
                        promotionLevels = promotionLevels,
                        validationStamps = validationStamps,
                        excludedPromotionRunId = excludedPromotionRunId,
                        requires = requires,
                    )
                } catch (ex: Exception) {
                    logger.error(
                        "Could not revoke the ${promotionLevel.name} promotion of build ${build.entityDisplayName}",
                        ex
                    )
                    meterRegistry.increment(
                        AutoPromotionMetrics.autoPromotionRevokeErrorCount,
                        AutoPromotionMetrics.Tags.PROJECT to branch.project.name,
                        AutoPromotionMetrics.Tags.PROMOTION_LEVEL to promotionLevel.name,
                    )
                }
            }
        }
    }

    private fun revokePromotionLevel(
        build: Build,
        promotionLevel: PromotionLevel,
        promotionLevels: List<PromotionLevel>,
        validationStamps: List<ValidationStamp>,
        excludedPromotionRunId: Int?,
        requires: (AutoPromotionProperty) -> Boolean,
    ) {
        val property = propertyService.getPropertyValue(promotionLevel, AutoPromotionPropertyType::class.java)
            ?: return
        // Opting out, or a property with nothing to check, means nothing to revoke
        if (!property.autoRevoke || property.isEmpty()) return
        // Nothing this event invalidated is a prerequisite of this promotion level
        if (!requires(property)) return
        // Runs to revoke, discounting the one being deleted. When a promotion level carries several runs,
        // the earlier delete events still see the remaining ones and the cascade only fires on the last one.
        val runs = structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)
            .filter { it.id() != excludedPromotionRunId }
        if (runs.isEmpty()) return
        // Nothing to do while the prerequisites still hold
        if (autoPromotionPrerequisites.areSatisfied(
                build = build,
                property = property,
                branchPromotionLevels = promotionLevels,
                branchValidationStamps = validationStamps,
                excludedPromotionRunId = excludedPromotionRunId,
            )
        ) {
            return
        }
        val inProgress = revocationsInProgress.get()
        val key = build.id() to promotionLevel.id()
        // Already being revoked further up the stack: the outer frame deletes the runs and posts the event
        if (!inProgress.add(key)) return
        try {
            // Opting into `autoRevoke` declares the promotion level fully auto-managed, so *every* run for it
            // on this build goes - including manually created ones. Leaving one standing would show the build
            // as promoted while its prerequisites are red.
            runs.forEach { run ->
                structureService.deletePromotionRun(run.id)
            }
            // Posted only once the deletion has succeeded, and in addition to `DELETE_PROMOTION_RUN`: the core
            // event says the run is gone, this one says why. Without it a subscriber cannot tell an automatic
            // revocation from a human clicking delete.
            eventPostService.post(eventFactory.autoPromotionRevoked(build, promotionLevel))
            meterRegistry.increment(
                AutoPromotionMetrics.autoPromotionRevokeCount,
                AutoPromotionMetrics.Tags.PROJECT to build.project.name,
                AutoPromotionMetrics.Tags.PROMOTION_LEVEL to promotionLevel.name,
            )
        } finally {
            inProgress -= key
            if (inProgress.isEmpty()) revocationsInProgress.remove()
        }
    }
}
