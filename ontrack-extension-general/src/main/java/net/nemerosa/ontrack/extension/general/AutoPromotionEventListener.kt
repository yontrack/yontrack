package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.PromotionRun.Companion.of
import org.springframework.stereotype.Component

/**
 * When a new validation run is created with a passed status, when the status of an existing validation run
 * becomes passed (`FIXED` for example), or when a promotion is granted, we check all auto promoted promotion levels
 * to know if each of their validation stamps is now passed.
 */
@Component
class AutoPromotionEventListener(
    private val structureService: StructureService,
    private val promotionRunService: PromotionRunService,
    private val propertyService: PropertyService,
    private val securityService: SecurityService,
    private val autoPromotionPrerequisites: AutoPromotionPrerequisites,
) : EventListener {

    override fun onEvent(event: Event) {
        when {
            event.eventType === EventFactory.NEW_VALIDATION_RUN ||
                    event.eventType === EventFactory.NEW_VALIDATION_RUN_STATUS -> onValidationRunStatus(event)
            event.eventType === EventFactory.DELETE_VALIDATION_STAMP -> onDeleteValidationStamp(event)
            event.eventType === EventFactory.NEW_PROMOTION_RUN -> onNewPromotionRun(event)
            event.eventType === EventFactory.DELETE_PROMOTION_LEVEL -> onDeletePromotionLevel(event)
        }
    }

    private fun onDeleteValidationStamp(event: Event) {
        // Gets the validation stamp ID
        val validationStampId = event.getIntValue("VALIDATION_STAMP_ID")
        // Branch
        val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
        // Gets all promotion levels for this branch
        val promotionLevels = structureService.getPromotionLevelListForBranch(branch.id)
        // Checks all promotion levels
        promotionLevels.forEach { promotionLevel: PromotionLevel ->
            cleanPromotionLevelFromValidationStamp(
                promotionLevel,
                validationStampId
            )
        }
    }

    private fun onDeletePromotionLevel(event: Event) {
        // Gets the promotion level ID
        val promotionLevelId = event.getIntValue("PROMOTION_LEVEL_ID")
        // Branch
        val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
        // Gets all promotion levels for this branch
        val promotionLevels = structureService.getPromotionLevelListForBranch(branch.id)
        // Checks all promotion levels
        promotionLevels.forEach { promotionLevel: PromotionLevel ->
            cleanPromotionLevelFromPromotionLevel(
                promotionLevel,
                promotionLevelId
            )
        }
    }

    private fun cleanPromotionLevelFromValidationStamp(promotionLevel: PromotionLevel, validationStampId: Int) {
        val property = propertyService.getPropertyValue(promotionLevel, AutoPromotionPropertyType::class.java)
        if (property != null) {
            val keptValidationStamps = property.validationStamps
                .filter { validationStamp: ValidationStamp -> (validationStampId != validationStamp.id()) }
            if (keptValidationStamps.size < property.validationStamps.size) {
                val editedProperty = property.copy(validationStamps = keptValidationStamps)
                securityService.asAdmin {
                    propertyService.editProperty(
                        promotionLevel,
                        AutoPromotionPropertyType::class.java,
                        editedProperty
                    )
                }
            }
        }
    }

    private fun cleanPromotionLevelFromPromotionLevel(promotionLevel: PromotionLevel, promotionLevelId: Int) {
        val property = propertyService.getPropertyValue(promotionLevel, AutoPromotionPropertyType::class.java)
        if (property != null) {
            val keptPromotionLevels =
                property.promotionLevels.filter { pl: PromotionLevel -> (promotionLevelId != pl.id()) }
            if (keptPromotionLevels.size < property.promotionLevels.size) {
                val editedProperty = property.copy(promotionLevels = keptPromotionLevels)
                securityService.asAdmin {
                    propertyService.editProperty(
                        promotionLevel,
                        AutoPromotionPropertyType::class.java,
                        editedProperty
                    )
                }
            }
        }
    }

    /**
     * Called when a validation run is created or when the status of an existing run changes.
     *
     * The auto promotions are checked only when the new status is a passed one - `PASSED` but
     * also `FIXED` (see #1629).
     */
    private fun onValidationRunStatus(event: Event) {
        // Passed validation?
        val validationRun = event.getEntity<ValidationRun>(ProjectEntityType.VALIDATION_RUN)
        if (validationRun.isPassed) {
            processEvent(event)
        }
    }

    private fun onNewPromotionRun(event: Event) {
        processEvent(event)
    }

    private fun processEvent(event: Event) {
        // Branch
        val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
        // Build
        val build = event.getEntity<Build>(ProjectEntityType.BUILD)
        // Gets all promotion levels for this branch
        val promotionLevels = structureService.getPromotionLevelListForBranch(branch.id)
        // Gets all validation stamps for this branch
        val validationStamps = structureService.getValidationStampListForBranch(branch.id)
        // Gets the promotion levels which have an auto promotion property
        promotionLevels.forEach { promotionLevel: PromotionLevel ->
            checkPromotionLevel(
                build,
                promotionLevel,
                promotionLevels,
                validationStamps
            )
        }
    }

    private fun checkPromotionLevel(
        build: Build,
        promotionLevel: PromotionLevel,
        promotionLevels: List<PromotionLevel>,
        validationStamps: List<ValidationStamp>
    ) {
        val property = propertyService.getPropertyValue(promotionLevel, AutoPromotionPropertyType::class.java)
        if (property != null) {
            // Checks if the property is eligible
            if (property.isEmpty()) {
                return
            }
            // Check to be done only if the promotion level is not attributed yet
            val isPromoted = promotionRunService.isBuildPromoted(build, promotionLevel)
            if (!isPromoted) {
                // Promotion is needed when all the prerequisites are satisfied
                if (autoPromotionPrerequisites.areSatisfied(
                        build = build,
                        property = property,
                        branchPromotionLevels = promotionLevels,
                        branchValidationStamps = validationStamps,
                    )
                ) {
                    // Promotes
                    // Makes sure to raise the auth level because the one
                    // having made a validation might not be granted to
                    // creation a promotion
                    securityService.asAdmin {
                        structureService.newPromotionRun(
                            of(
                                build,
                                promotionLevel,
                                securityService.currentSignature,
                                "Auto promotion"
                            )
                        )
                    }
                }
            }
        }
    }

}
