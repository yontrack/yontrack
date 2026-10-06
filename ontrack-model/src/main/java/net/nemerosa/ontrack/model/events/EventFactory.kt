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

    /**
     * @param project The project, as saved
     * @param previousName Previous name of the project, when the update renames it
     */
    fun updateProject(project: Project, previousName: String? = null): Event

    fun disableProject(project: Project): Event

    fun enableProject(project: Project): Event

    fun deleteProject(project: Project): Event

    fun newBranch(branch: Branch): Event

    /**
     * @param branch The branch, as saved
     * @param previousName Previous name of the branch, when the update renames it
     */
    fun updateBranch(branch: Branch, previousName: String? = null): Event

    fun disableBranch(branch: Branch): Event

    fun enableBranch(branch: Branch): Event

    fun deleteBranch(branch: Branch): Event

    fun newBuild(build: Build): Event

    /**
     * The [build] has been saved, replacing the [previous] values of its name, description and
     * signature.
     */
    fun updateBuild(build: Build, previous: Build): Event

    fun updateBuildDisplayName(build: Build, displayName: String): Event

    fun deleteBuild(build: Build): Event

    /**
     * A link from the [from] build to the [to] build has been added with [qualifier].
     */
    fun newBuildLink(from: Build, to: Build, qualifier: String): Event

    /**
     * The link from the [from] build to the [to] build with [qualifier] has been deleted.
     */
    fun deleteBuildLink(from: Build, to: Build, qualifier: String): Event

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

    /**
     * The comment of the status [validationRunStatusId] of the [validationRun] has been replaced by
     * [comment].
     */
    fun updateValidationRunStatusComment(validationRun: ValidationRun, validationRunStatusId: ID, comment: String): Event

    /**
     * The [validationRun] is about to be deleted.
     */
    fun deleteValidationRun(validationRun: ValidationRun): Event

    /**
     * The data of the [validationRun] has been replaced or removed. The run carries its new data.
     */
    fun updateValidationRunData(validationRun: ValidationRun): Event

    /**
     * The [runInfo] of a build or a validation run has been set, created or replaced.
     */
    fun updateRunInfo(entity: RunnableEntity, runInfo: RunInfo): Event

    /**
     * The run info of a build or a validation run has been deleted.
     */
    fun deleteRunInfo(entity: RunnableEntity): Event

    fun <T> propertyChange(entity: ProjectEntity, propertyType: PropertyType<T>): Event

    fun <T> propertyDelete(entity: ProjectEntity, propertyType: PropertyType<T>): Event

    // Configurations
    fun <T : Configuration<T>> newConfiguration(configuration: T): Event

    fun <T : Configuration<T>> updateConfiguration(configuration: T): Event

    fun <T : Configuration<T>> deleteConfiguration(configuration: T): Event

    // Getting the list of all possible event types
    val eventTypes: Collection<EventType>

    companion object {

        /**
         * Value of the update events of the projects and branches holding their previous name, when
         * the update renames them
         */
        const val PREVIOUS_NAME = "PREVIOUS_NAME"

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
                    eventValue(PREVIOUS_NAME, "Previous name of the project, only when the update renames it"),
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
                    eventValue(PREVIOUS_NAME, "Previous name of the branch, only when the update renames it"),
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
            description = "When a build is updated. A previous value which was not set is absent from the event.",
            context = eventContext(
                eventProject("Build's project"),
                eventBranch("Build's branch"),
                eventBuild("Updated build"),
                eventValue(PREVIOUS_BUILD_NAME, "Name of the build before the update"),
                eventValue(PREVIOUS_BUILD_DESCRIPTION, "Description of the build before the update"),
                eventValue(PREVIOUS_BUILD_CREATION, "Creation time of the build before the update, ISO-8601 UTC"),
                eventValue(PREVIOUS_BUILD_CREATOR, "Creator of the build before the update"),
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

        val NEW_BUILD_LINK: EventType = SimpleEventType(
            id = "new_build_link",
            template = "Build \${build} for branch \${branch} in \${project} has been linked to build \${xBuild} for branch \${xBranch} in \${xProject}.",
            description = "When a link from a build to another build is added.",
            context = eventContext(
                eventProject("Project of the source build"),
                eventBranch("Branch of the source build"),
                eventBuild("Source build, which uses the target build"),
                eventXProject("Project of the target build"),
                eventXBranch("Branch of the target build"),
                eventXBuild("Target build, used by the source build"),
                eventValue(QUALIFIER, "Qualifier of the link, empty for the default link"),
            ),
        )

        val DELETE_BUILD_LINK: EventType = SimpleEventType(
            id = "delete_build_link",
            template = "Build \${build} for branch \${branch} in \${project} is no longer linked to build \${xBuild} for branch \${xBranch} in \${xProject}.",
            description = "When a link from a build to another build is deleted.",
            context = eventContext(
                eventProject("Project of the source build"),
                eventBranch("Branch of the source build"),
                eventBuild("Source build, which used the target build"),
                eventXProject("Project of the target build"),
                eventXBranch("Branch of the target build"),
                eventXBuild("Target build, which was used by the source build"),
                eventValue(QUALIFIER, "Qualifier of the link, empty for the default link"),
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
                eventValue(VALIDATION_RUN_STATUS_ID, "ID of the status whose comment has changed"),
                eventValue(VALIDATION_RUN_STATUS_COMMENT, "New comment of the status"),
            ),
        )

        private val runInfoEntityContext = eventContext(
            eventAnyEntity("Build or validation run whose run info has changed"),
            eventProject("Project"),
            eventBranch("Branch"),
            eventBuild("Build, or build of the validation run"),
            eventValidationStamp("Validation stamp of the validation run - for a validation run only"),
            eventValidationRun("Validation run - for a validation run only"),
            eventValue(RUNNABLE_ENTITY_TYPE, "Type of the entity: build or validation_run"),
        )

        val UPDATE_RUN_INFO: EventType = SimpleEventType(
            id = "update_run_info",
            template = "Run info of \${entity.qualifiedLongName} has been set.",
            description = "When the run info of a build or of a validation run is set, created or replaced. " +
                    "A value which is not set is absent from the event.",
            context = runInfoEntityContext.add(
                eventValue(RUN_INFO_SOURCE_TYPE, "Type of source (like github)"),
                eventValue(RUN_INFO_SOURCE_URI, "URI to the source of the run"),
                eventValue(RUN_INFO_TRIGGER_TYPE, "Type of trigger (like scm or user)"),
                eventValue(RUN_INFO_TRIGGER_DATA, "Data associated with the trigger (like a user ID or a commit)"),
                eventValue(RUN_INFO_RUN_TIME, "Time of the run, in seconds"),
            ),
        )

        val DELETE_RUN_INFO: EventType = SimpleEventType(
            id = "delete_run_info",
            template = "Run info of \${entity.qualifiedLongName} has been deleted.",
            description = "When the run info of a build or of a validation run is deleted.",
            context = runInfoEntityContext,
        )

        val DELETE_VALIDATION_RUN: EventType = SimpleEventType(
            id = "delete_validation_run",
            template = "Validation run #\${VALIDATION_RUN_ORDER} for \${validationStamp} of build \${build} has been deleted for branch \${branch} in \${project}.",
            description = "When the validation run of a build is deleted.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Build of the deleted validation run"),
                eventValidationStamp("Validation stamp of the deleted validation run"),
                eventValue(VALIDATION_RUN_ID, "ID of the deleted validation run"),
                eventValue(VALIDATION_RUN_ORDER, "Order of the deleted validation run for its build, starting at 1"),
                eventValue("STATUS", "ID of the last status of the deleted validation run"),
                eventValue("STATUS_NAME", "Name of the last status of the deleted validation run"),
            ),
        )

        val UPDATE_VALIDATION_RUN_DATA: EventType = SimpleEventType(
            id = "update_validation_run_data",
            template = "Data of the \${validationStamp} validation \${validationRun} for build \${build} in branch \${branch} of \${project} has changed.",
            description = "When the data of the validation of a build is replaced or removed. " +
                    "When the data is removed, the event carries no data type and no data.",
            context = eventContext(
                eventProject("Project"),
                eventBranch("Branch"),
                eventBuild("Validated build"),
                eventValidationStamp("Validation stamp"),
                eventValidationRun("Validation run"),
                eventValue(VALIDATION_RUN_DATA_TYPE, "ID of the new data type"),
                eventValue(VALIDATION_RUN_DATA, "New data, as JSON"),
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
        const val PREVIOUS_BUILD_NAME = "PREVIOUS_BUILD_NAME"
        const val PREVIOUS_BUILD_DESCRIPTION = "PREVIOUS_BUILD_DESCRIPTION"
        const val PREVIOUS_BUILD_CREATION = "PREVIOUS_BUILD_CREATION"
        const val PREVIOUS_BUILD_CREATOR = "PREVIOUS_BUILD_CREATOR"
        const val QUALIFIER = "QUALIFIER"
        const val VALIDATION_RUN_ID = "VALIDATION_RUN_ID"
        const val VALIDATION_RUN_ORDER = "VALIDATION_RUN_ORDER"
        const val VALIDATION_RUN_STATUS_ID = "VALIDATION_RUN_STATUS_ID"
        const val VALIDATION_RUN_STATUS_COMMENT = "VALIDATION_RUN_STATUS_COMMENT"
        const val VALIDATION_RUN_DATA_TYPE = "DATA_TYPE"
        const val VALIDATION_RUN_DATA = "DATA"
        const val RUNNABLE_ENTITY_TYPE = "RUNNABLE_ENTITY_TYPE"
        const val RUN_INFO_SOURCE_TYPE = "SOURCE_TYPE"
        const val RUN_INFO_SOURCE_URI = "SOURCE_URI"
        const val RUN_INFO_TRIGGER_TYPE = "TRIGGER_TYPE"
        const val RUN_INFO_TRIGGER_DATA = "TRIGGER_DATA"
        const val RUN_INFO_RUN_TIME = "RUN_TIME"
    }
}
