package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.Configuration

/**
 * Factory for events.
 */
interface EventFactory {

    /**
     * Gets an event type using its [EventType.id]  id}.
     */
    fun toEventType(id: String): EventType

    /**
     * Allows a third-party extension to register an additional event type.
     *
     * @param eventType Event type to register.
     */
    fun register(eventType: EventType)

    // List of known events
    fun newProject(project: Project): Event

    fun updateProject(project: Project): Event

    fun disableProject(project: Project): Event

    fun enableProject(project: Project): Event

    fun deleteProject(project: Project): Event

    fun newBranch(branch: Branch): Event

    fun updateBranch(branch: Branch): Event

    fun disableBranch(branch: Branch): Event

    fun enableBranch(branch: Branch): Event

    fun deleteBranch(branch: Branch): Event

    fun newBuild(build: Build): Event

    fun updateBuild(build: Build): Event

    fun updateBuildDisplayName(build: Build, displayName: String): Event

    fun deleteBuild(build: Build): Event

    fun newPromotionLevel(promotionLevel: PromotionLevel): Event

    fun imagePromotionLevel(promotionLevel: PromotionLevel): Event

    fun updatePromotionLevel(promotionLevel: PromotionLevel): Event

    fun deletePromotionLevel(promotionLevel: PromotionLevel): Event

    fun reorderPromotionLevels(branch: Branch): Event

    fun newPromotionRun(promotionRun: PromotionRun): Event

    fun deletePromotionRun(promotionRun: PromotionRun): Event

    /**
     * An auto promotion has been revoked on a [build] because one of the prerequisites of the
     * [promotionLevel] is no longer valid. Posted in addition to [deletePromotionRun].
     */
    fun autoPromotionRevoked(build: Build, promotionLevel: PromotionLevel): Event

    fun newValidationStamp(validationStamp: ValidationStamp): Event

    fun imageValidationStamp(validationStamp: ValidationStamp): Event

    fun updateValidationStamp(validationStamp: ValidationStamp): Event

    fun deleteValidationStamp(validationStamp: ValidationStamp): Event

    fun reorderValidationStamps(branch: Branch): Event

    fun newValidationRun(validationRun: ValidationRun): Event

    fun newValidationRunStatus(validationRun: ValidationRun): Event

    fun updateValidationRunStatusComment(validationRun: ValidationRun): Event

    fun <T> propertyChange(entity: ProjectEntity, propertyType: PropertyType<T>): Event

    fun <T> propertyDelete(entity: ProjectEntity, propertyType: PropertyType<T>): Event

    // Configurations
    fun <T : Configuration<T>> newConfiguration(configuration: T): Event

    fun <T : Configuration<T>> updateConfiguration(configuration: T): Event

    fun <T : Configuration<T>> deleteConfiguration(configuration: T): Event

    // Getting the list of all possible event types
    val eventTypes: Collection<EventType>

    companion object {
        val NEW_PROJECT: EventType =
            SimpleEventType(
                id = "new_project",
                template = "New project \${project}.",
                description = "When a project is created.",
                context = eventContext(
                    eventProject("Created project"),
                ),
            )

        val UPDATE_PROJECT: EventType =
            SimpleEventType(
                id = "update_project",
                template = "Project \${project} has been updated.",
                description = "When a project is updated.",
                context = eventContext(
                    eventProject("Updated project"),
                ),
            )

        val ENABLE_PROJECT: EventType =
            SimpleEventType(
                id = "enable_project",
                template = "Project \${project} has been enabled.",
                description = "When a project becomes enabled again.",
                context = eventContext(
                    eventProject("Enabled project"),
                ),
            )

        val DISABLE_PROJECT: EventType =
            SimpleEventType(
                id = "disable_project",
                template = "Project \${project} has been disabled.",
                description = "When a project is disabled.",
                context = eventContext(
                    eventProject("Disabled project"),
                ),
            )

        val DELETE_PROJECT: EventType =
            SimpleEventType(
                id = "delete_project",
                template = "Project \${PROJECT} has been deleted.",
                description = "When a project is deleted.",
                context = eventContext(
                    eventValue("PROJECT", "Name of the deleted project"),
                ),
            )

        val NEW_BRANCH: EventType =
            SimpleEventType(
                id = "new_branch",
                template = "New branch \${branch} for project \${project}.",
                description = "When a branch is created.",
                context = eventContext(
                    eventProject("Branch's project"),
                    eventBranch("Created branch"),
                ),
            )

        val UPDATE_BRANCH: EventType =
            SimpleEventType(
                id = "update_branch",
                template = "Branch \${branch} in \${project} has been updated.",
                description = "When a branch is updated.",
                context = eventContext(
                    eventProject("Branch's project"),
                    eventBranch("Updated branch"),
                ),
            )
        val ENABLE_BRANCH: EventType =
            SimpleEventType(
                id = "enable_branch",
                template = "Branch \${branch} in \${project} has been enabled.",
                description = "When a branch becomes enabled again.",
                context = eventContext(
                    eventProject("Branch's project"),
                    eventBranch("Enabled branch"),
                ),
            )

        val DISABLE_BRANCH: EventType =
            SimpleEventType(
                id = "disable_branch",
                template = "Branch \${branch} in \${project} has been disabled.",
                description = "When a branch is disabled.",
                context = eventContext(
                    eventProject("Branch's project"),
                    eventBranch("Disabled branch"),
                ),
            )
        val DELETE_BRANCH: EventType =
            SimpleEventType(
                id = "delete_branch",
                template = "Branch \${BRANCH} has been deleted from \${project}.",
                description = "When a branch is deleted.",
                context = eventContext(
                    eventProject("Branch's project"),
                    eventValue("BRANCH", "Name of the deleted branch"),
                ),
            )

        val NEW_BUILD: EventType =
            SimpleEventType(
                id = "new_build",
                template = "New build \${build} for branch \${branch} in \${project}.",
                description = "When a build is created.",
                context = eventContext(
                    eventProject("Build's project"),
                    eventBranch("Build's branch"),
                    eventBuild("Created build"),
                ),
            )
        val UPDATE_BUILD: EventType = SimpleEventType(
            id = "update_build",
            template = "Build \${build} for branch \${branch} in \${project} has been updated.",
            description = "When a build is updated.",
            context = eventContext(
                eventProject("Build's project"),
                eventBranch("Build's branch"),
                eventBuild("Updated build"),
            ),
        )
        val UPDATE_BUILD_DISPLAY_NAME: EventType = SimpleEventType(
            id = "update_build_display_name",
            template = "Display name of build \${build} for branch \${branch} in \${project} has been updated to \${DISPLAY_NAME}.",
            description = "When a build display name is updated.",
            context = eventContext(
                eventProject("Build's project"),
                eventBranch("Build's branch"),
                eventBuild("Updated build"),
                eventValue(DISPLAY_NAME, "New display name"),
            ),
        )
        val DELETE_BUILD: EventType = SimpleEventType(
            id = "delete_build",
            template = "Build \${BUILD} for branch \${branch} in \${project} has been deleted.",
            description = "When a build is deleted.",
            context = eventContext(
                eventProject("Build's project"),
                eventBranch("Build's branch"),
                eventValue("BUILD", "Name of the deleted build"),
            ),
        )

        val NEW_PROMOTION_LEVEL: EventType = SimpleEventType(
            id = "new_promotion_level",
            template = "New promotion level \${promotionLevel} for branch \${branch} in \${project}.",
            description = "When a promotion level is created.",
            context = eventContext(
                eventProject("Promotion level's project"),
                eventBranch("Promotion level's branch"),
                eventPromotionLevel("Created promotion level"),
            ),
        )
        val IMAGE_PROMOTION_LEVEL: EventType = SimpleEventType(
            id = "image_promotion_level",
            template = "Image for promotion level \${promotionLevel} for branch \${branch} in \${project} has changed.",
            description = "When a promotion level's image is updated.",
            context = eventContext(
                eventProject("Promotion level's project"),
                eventBranch("Promotion level's branch"),
                eventPromotionLevel("Updated promotion level"),
            ),
        )
        val UPDATE_PROMOTION_LEVEL: EventType = SimpleEventType(
            id = "update_promotion_level",
            template = "Promotion level \${promotionLevel} for branch \${branch} in \${project} has changed.",
            description = "When a promotion level is updated.",
            context = eventContext(
                eventProject("Promotion level's project"),
                eventBranch("Promotion level's branch"),
                eventPromotionLevel("Updated promotion level"),
            ),
        )
        val DELETE_PROMOTION_LEVEL: EventType = SimpleEventType(
            id = "delete_promotion_level",
            template = "Promotion level \${PROMOTION_LEVEL} for branch \${branch} in \${project} has been deleted.",
            description = "When a promotion level is deleted.",
            context = eventContext(
                eventProject("Promotion level's project"),
                eventBranch("Promotion level's branch"),
                eventValue("PROMOTION_LEVEL", "Deleted promotion level"),
                eventValue("PROMOTION_LEVEL_ID", "ID of the deleted promotion level"),
            ),
        )
        val REORDER_PROMOTION_LEVEL: EventType = SimpleEventType(
            id = "reorder_promotion_level",
            template = "Promotion levels for branch \${branch} in \${project} have been reordered.",
            description = "When the promotion levels of a branch are reordered.",
            context = eventContext(
                eventProject("Promotion levels project"),
                eventBranch("Promotion levels branch"),
            ),
        )

        val NEW_VALIDATION_STAMP: EventType = SimpleEventType(
            id = "new_validation_stamp",
            template = "New validation stamp \${validationStamp} for branch \${branch} in \${project}.",
            description = "When a validation stamp is created.",
            context = eventContext(
                eventProject("Validation stamp's project"),
                eventBranch("Validation stamp's branch"),
                eventValidationStamp("Created validation stamp"),
            ),
        )
        val IMAGE_VALIDATION_STAMP: EventType = SimpleEventType(
            id = "image_validation_stamp",
            template = "Image for validation stamp \${validationStamp} for branch \${branch} in \${project} has changed.",
            description = "When a validation stamp's image is updated.",
            context = eventContext(
                eventProject("Validation stamp's project"),
                eventBranch("Validation stamp's branch"),
                eventValidationStamp("Updated validation stamp"),
            ),
        )
        val UPDATE_VALIDATION_STAMP: EventType = SimpleEventType(
            id = "update_validation_stamp",
            template = "Validation stamp \${validationStamp} for branch \${branch} in \${project} has been updated.",
            description = "When a validation stamp is updated.",
            context = eventContext(
                eventProject("Validation stamp's project"),
                eventBranch("Validation stamp's branch"),
                eventValidationStamp("Updated validation stamp"),
            ),
        )
        val DELETE_VALIDATION_STAMP: EventType = SimpleEventType(
            id = "delete_validation_stamp",
            template = "Validation stamp \${VALIDATION_STAMP} for branch \${branch} in \${project} has been deleted.",
            description = "When a validation stamp is deleted.",
            context = eventContext(
                eventProject("Validation stamp's project"),
                eventBranch("Validation stamp's branch"),
                eventValue("VALIDATION_STAMP", "Name of the deleted validation stamp"),
                eventValue("VALIDATION_STAMP_ID", "ID of the deleted validation stamp"),
            ),
        )
        val REORDER_VALIDATION_STAMP: EventType = SimpleEventType(
            id = "reorder_validation_stamp",
            template = "Validation stamps for branch \${branch} in \${project} have been reordered.",
            description = "When the validation stamps of a branch are reordered.",
            context = eventContext(
                eventProject("Validation stamps project"),
                eventBranch("Validation stamps branch"),
            ),
        )

        val NEW_PROMOTION_RUN: EventType = SimpleEventType(
            id = "new_promotion_run",
            template = "Build \${build} has been promoted to \${promotionLevel} for branch \${branch} in \${project}.",
            description = "When a build is promoted.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Promoted build"),
                eventPromotionLevel("Promotion level"),
                eventPromotionRun("Promotion run"),
            ),
        )
        val DELETE_PROMOTION_RUN: EventType = SimpleEventType(
            id = "delete_promotion_run",
            template = "Promotion \${promotionLevel} of build \${build} has been deleted for branch \${branch} in \${project}.",
            description = "When the promotion of a build is deleted.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Promoted build"),
                eventPromotionLevel("Promotion level"),
            ),
        )

        val AUTO_PROMOTION_REVOKED: EventType = SimpleEventType(
            id = "auto_promotion_revoked",
            template = "Promotion \${promotionLevel} of build \${build} has been revoked for branch \${branch} in \${project} because one of its prerequisites is no longer valid.",
            description = "When an auto promotion is revoked because one of its prerequisites - a required " +
                    "validation stamp or a required promotion - is no longer valid. This event is posted in " +
                    "addition to the deletion of the promotion run itself.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Build whose promotion has been revoked"),
                eventPromotionLevel("Revoked promotion level"),
            ),
        )

        val NEW_VALIDATION_RUN: EventType = SimpleEventType(
            id = "new_validation_run",
            template = "Build \${build} has run for the \${validationStamp} with status \${STATUS_NAME} in branch \${branch} in \${project}.",
            description = "When a build is validated.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Validated build"),
                eventValidationStamp("Validation stamp"),
                eventValidationRun("Validation run"),
                eventValue("STATUS", "ID of the validation run status"),
                eventValue("STATUS_NAME", "Name of the validation run status"),
            ),
        )
        val NEW_VALIDATION_RUN_STATUS: EventType = SimpleEventType(
            id = "new_validation_run_status",
            template = "Status for the \${validationStamp} validation \${validationRun} for build \${build} in branch \${branch} of \${project} has changed to \${STATUS_NAME}.",
            description = "When the status of the validation of a build is updated.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Validated build"),
                eventValidationStamp("Validation stamp"),
                eventValidationRun("Validation run"),
                eventValue("STATUS", "ID of the validation run status"),
                eventValue("STATUS_NAME", "Name of the validation run status"),
            ),
        )
        val UPDATE_VALIDATION_RUN_STATUS_COMMENT: EventType = SimpleEventType(
            id = "update_validation_run_status_comment",
            template = "A status message for the \${validationStamp} validation \${validationRun} for build \${build} in branch \${branch} of \${project} has changed.",
            description = "When the status message of the validation of a build is updated.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Validated build"),
                eventValidationStamp("Validation stamp"),
                eventValidationRun("Validation run"),
            ),
        )

        val PROPERTY_CHANGE: EventType = SimpleEventType(
            id = "property_change",
            template = "\${PROPERTY_NAME} property has changed for \${entity.qualifiedLongName}.",
            description = "When a property is edited.",
            context = eventContext(
                eventAnyEntity("Entity where the property has been edited"),
                eventValue("PROPERTY", "FQCN of the property type"),
                eventValue("PROPERTY_NAME", "Display name of the property"),
            ),
        )
        val PROPERTY_DELETE: EventType = SimpleEventType(
            id = "property_delete",
            template = "\${PROPERTY_NAME} property has been removed from \${entity.qualifiedLongName}.",
            description = "When a property is deleted.",
            context = eventContext(
                eventAnyEntity("Entity where the property has been edited"),
                eventValue("PROPERTY", "FQCN of the property type"),
                eventValue("PROPERTY_NAME", "Display name of the property"),
            ),
        )

        val NEW_CONFIGURATION: EventType = SimpleEventType(
            id = "new_configuration",
            template = "\${CONFIGURATION} configuration has been created.",
            description = "When a configuration is created.",
            context = eventContext(
                eventValue("CONFIGURATION", "Name of the configuration")
            ),
        )
        val UPDATE_CONFIGURATION: EventType = SimpleEventType(
            id = "update_configuration",
            template = "\${CONFIGURATION} configuration has been updated.",
            description = "When a configuration is updated.",
            context = eventContext(
                eventValue("CONFIGURATION", "Name of the configuration")
            ),
        )
        val DELETE_CONFIGURATION: EventType = SimpleEventType(
            id = "delete_configuration",
            template = "\${CONFIGURATION} configuration has been deleted.",
            description = "When a configuration is deleted.",
            context = eventContext(
                eventValue("CONFIGURATION", "Name of the configuration")
            ),
        )

        const val DISPLAY_NAME = "DISPLAY_NAME"
    }
}
