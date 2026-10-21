package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunService
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Reason of the system acting as administrator to validate a build on the promotion of a build it links to.
 */
private const val VALIDATION_ON_PROMOTION = "validation-on-promotion"

/**
 * Size of the pages when going through the builds which use a promoted build.
 */
private const val PAGE_SIZE = 100

/**
 * Creates the runs of the [OnPromotionValidationDataType] stamps:
 *
 * 1. when a build reaches a promotion, the builds using it are validated;
 * 2. when a build links to an already promoted build, it is validated;
 * 3. when the last run of this promotion is deleted, the runs it granted are followed by a failed one.
 *
 * A failure here is logged, and does not stop the processing of the other builds and stamps. Like
 * [net.nemerosa.ontrack.extension.general.AutoPromotionEventListener], this listener runs in the transaction
 * of the event: a failure of the creation of a run, which marks this transaction for rollback, still fails
 * the promotion or the link which triggered it.
 */
@Component
class OnPromotionValidationEventListener(
    private val structureService: StructureService,
    private val validationRunService: ValidationRunService,
    private val securityService: SecurityService,
) : EventListener {

    private val logger: Logger = LoggerFactory.getLogger(OnPromotionValidationEventListener::class.java)

    private val dataTypeId = OnPromotionValidationDataType::class.java.name

    override fun onEvent(event: Event) {
        when {
            event.eventType === EventFactory.NEW_PROMOTION_RUN -> safely(event) { onNewPromotionRun(event) }
            event.eventType === EventFactory.NEW_BUILD_LINK -> safely(event) { onNewBuildLink(event) }
            event.eventType === EventFactory.DELETE_PROMOTION_RUN -> safely(event) { onDeletePromotionRun(event) }
        }
    }

    /**
     * Listeners run inside the transaction of the event: an exception escaping here would fail the promotion
     * or the link which triggered it.
     */
    private fun safely(event: Event, code: () -> Unit) {
        try {
            securityService.asAdmin(VALIDATION_ON_PROMOTION) {
                code()
            }
        } catch (ex: Exception) {
            logger.error("Could not process the validations on promotion for event ${event.eventType.id}", ex)
        }
    }

    /**
     * Trigger 1 - the builds using the promoted build are validated.
     */
    private fun onNewPromotionRun(event: Event) {
        val promotionRun = event.getEntity<PromotionRun>(ProjectEntityType.PROMOTION_RUN)
        val promoted = promotionRun.build
        val promotionLevel = promotionRun.promotionLevel
        forEachMatchingStamp(promoted, promotionLevel) { consumer, validationStamp ->
            validate(consumer, validationStamp, promoted, promotionRun)
        }
    }

    /**
     * Trigger 2 - a build linking to an already promoted build is validated.
     */
    private fun onNewBuildLink(event: Event) {
        val consumer = event.getEntity<Build>(ProjectEntityType.BUILD)
        val linked = event.extraEntities[ProjectEntityType.BUILD] as? Build ?: return
        val qualifier = event.values[EventFactory.QUALIFIER]?.value ?: BuildLink.DEFAULT
        stampsOf(consumer)
            .filter { (_, config) -> config.matches(linked.project.name, qualifier) }
            .forEach { (validationStamp, config) ->
                isolated(consumer, validationStamp) {
                    val promotionLevel = structureService.findPromotionLevelByName(
                        linked.project.name,
                        linked.branch.name,
                        config.promotion,
                    ).orElse(null)
                    if (promotionLevel != null) {
                        val promotionRun = structureService.getPromotionRunsForBuildAndPromotionLevel(linked, promotionLevel)
                            .maxByOrNull { it.id() }
                        if (promotionRun != null) {
                            validate(consumer, validationStamp, linked, promotionRun)
                        }
                    }
                }
            }
    }

    /**
     * Trigger 3 - when the linked build loses its promotion, the runs it granted are followed by a failed one.
     */
    private fun onDeletePromotionRun(event: Event) {
        val promoted = event.getEntity<Build>(ProjectEntityType.BUILD)
        val promotionLevel = event.getEntity<PromotionLevel>(ProjectEntityType.PROMOTION_LEVEL)
        // The event is posted *before* the run leaves the database
        val deletedPromotionRunId = event.getIntValue("PROMOTION_RUN_ID")
        val stillPromoted = structureService.getPromotionRunsForBuildAndPromotionLevel(promoted, promotionLevel)
            .any { it.id() != deletedPromotionRunId }
        if (!stillPromoted) {
            forEachMatchingStamp(promoted, promotionLevel) { consumer, validationStamp ->
                revoke(consumer, validationStamp, promoted, promotionLevel)
            }
        }
    }

    /**
     * Goes through all the builds using the [promoted] build, and through their stamps matching the link
     * and the [promotionLevel].
     */
    private fun forEachMatchingStamp(
        promoted: Build,
        promotionLevel: PromotionLevel,
        code: (consumer: Build, validationStamp: ValidationStamp) -> Unit,
    ) {
        // Stamps per branch of the consumers, for this event only
        val stampsPerBranch = mutableMapOf<Int, List<Pair<ValidationStamp, OnPromotionValidationConfig>>>()
        var offset = 0
        do {
            val page = structureService.getQualifiedBuildsUsing(promoted, offset, PAGE_SIZE)
            page.pageItems.forEach { link ->
                val consumer = link.build
                stampsPerBranch.getOrPut(consumer.branch.id()) { stampsOf(consumer) }
                    .filter { (_, config) ->
                        config.matches(promoted.project.name, link.qualifier, promotionLevel.name)
                    }
                    .forEach { (validationStamp, _) ->
                        isolated(consumer, validationStamp) {
                            code(consumer, validationStamp)
                        }
                    }
            }
            offset += PAGE_SIZE
        } while (page.pageInfo.nextPage != null)
    }

    /**
     * One stamp of one build failing does not prevent the others from being processed.
     */
    private fun isolated(consumer: Build, validationStamp: ValidationStamp, code: () -> Unit) {
        try {
            code()
        } catch (ex: Exception) {
            logger.error(
                "Could not process the ${validationStamp.name} validation on promotion for build ${consumer.entityDisplayName}",
                ex
            )
        }
    }

    /**
     * Stamps of the branch of the [consumer] which are validations on promotion, with their configuration.
     */
    private fun stampsOf(consumer: Build): List<Pair<ValidationStamp, OnPromotionValidationConfig>> =
        structureService.getValidationStampListForBranch(consumer.branch.id)
            .mapNotNull { validationStamp ->
                val dataTypeConfig = validationStamp.dataType
                val config = dataTypeConfig?.config
                if (dataTypeConfig?.descriptor?.id == dataTypeId && config is OnPromotionValidationConfig) {
                    validationStamp to config
                } else {
                    null
                }
            }

    private fun validate(consumer: Build, validationStamp: ValidationStamp, promoted: Build, promotionRun: PromotionRun) {
        if (!validationRunService.isValidationRunPassed(consumer, validationStamp)) {
            structureService.newValidationRun(
                consumer,
                ValidationRunRequest(
                    validationStampName = validationStamp.name,
                    dataTypeId = dataTypeId,
                    data = OnPromotionValidationData(
                        buildId = promoted.id(),
                        project = promoted.project.name,
                        branch = promoted.branch.name,
                        build = promoted.name,
                        promotion = promotionRun.promotionLevel.name,
                        promotionRunId = promotionRun.id(),
                    ),
                    description = "Build ${promoted.entityDisplayName} has been promoted to ${promotionRun.promotionLevel.name}.",
                )
            )
        }
    }

    private fun revoke(consumer: Build, validationStamp: ValidationStamp, promoted: Build, promotionLevel: PromotionLevel) {
        val lastRun = structureService.getValidationRunsForBuildAndValidationStamp(consumer, validationStamp, 0, 1)
            .firstOrNull()
            ?: return
        val data = lastRun.data?.data as? OnPromotionValidationData
        // Only the run granted by this very build
        if (lastRun.isPassed && data != null && data.buildId == promoted.id()) {
            // `PASSED` is a terminal status, which cannot be followed by `FAILED` on the same run:
            // a new failed run is created instead
            structureService.newValidationRun(
                consumer,
                ValidationRunRequest(
                    validationStampName = validationStamp.name,
                    validationRunStatusId = ValidationRunStatusID.STATUS_FAILED,
                    description = "Build ${promoted.entityDisplayName} is no longer promoted to ${promotionLevel.name}.",
                )
            )
        }
    }
}
