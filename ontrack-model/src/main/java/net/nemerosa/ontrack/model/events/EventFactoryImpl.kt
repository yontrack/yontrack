package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.model.events.Event.Companion.of
import net.nemerosa.ontrack.model.events.EventFactory.Companion.AUTO_PROMOTION_REVOKED
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_BRANCH
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_BUILD
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_BUILD_LINK
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_CONFIGURATION
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_PROJECT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_PROMOTION_LEVEL
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_PROMOTION_RUN
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_VALIDATION_STAMP
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DISABLE_BRANCH
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DISABLE_PROJECT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DISPLAY_NAME
import net.nemerosa.ontrack.model.events.EventFactory.Companion.ENABLE_BRANCH
import net.nemerosa.ontrack.model.events.EventFactory.Companion.ENABLE_PROJECT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.IMAGE_PROMOTION_LEVEL
import net.nemerosa.ontrack.model.events.EventFactory.Companion.IMAGE_VALIDATION_STAMP
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_BRANCH
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_BUILD
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_BUILD_LINK
import net.nemerosa.ontrack.model.events.EventFactory.Companion.QUALIFIER
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_CONFIGURATION
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_PROJECT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_PROMOTION_LEVEL
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_PROMOTION_RUN
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_VALIDATION_RUN
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_VALIDATION_RUN_STATUS
import net.nemerosa.ontrack.model.events.EventFactory.Companion.NEW_VALIDATION_STAMP
import net.nemerosa.ontrack.model.events.EventFactory.Companion.PREVIOUS_BUILD_CREATION
import net.nemerosa.ontrack.model.events.EventFactory.Companion.PREVIOUS_BUILD_CREATOR
import net.nemerosa.ontrack.model.events.EventFactory.Companion.PREVIOUS_BUILD_DESCRIPTION
import net.nemerosa.ontrack.model.events.EventFactory.Companion.PREVIOUS_BUILD_NAME
import net.nemerosa.ontrack.model.events.EventFactory.Companion.PROPERTY_CHANGE
import net.nemerosa.ontrack.model.events.EventFactory.Companion.PROPERTY_DELETE
import net.nemerosa.ontrack.model.events.EventFactory.Companion.REORDER_PROMOTION_LEVEL
import net.nemerosa.ontrack.model.events.EventFactory.Companion.REORDER_VALIDATION_STAMP
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_BRANCH
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_BUILD
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_BUILD_DISPLAY_NAME
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_CONFIGURATION
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_PROJECT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_RUN_INFO
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_VALIDATION_RUN
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_VALIDATION_RUN_DATA
import net.nemerosa.ontrack.model.events.EventFactory.Companion.VALIDATION_RUN_DATA
import net.nemerosa.ontrack.model.events.EventFactory.Companion.VALIDATION_RUN_DATA_TYPE
import net.nemerosa.ontrack.model.events.EventFactory.Companion.VALIDATION_RUN_ID
import net.nemerosa.ontrack.model.events.EventFactory.Companion.VALIDATION_RUN_ORDER
import net.nemerosa.ontrack.model.events.EventFactory.Companion.VALIDATION_RUN_STATUS_COMMENT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.VALIDATION_RUN_STATUS_ID
import net.nemerosa.ontrack.model.events.EventFactory.Companion.DELETE_RUN_INFO
import net.nemerosa.ontrack.model.events.EventFactory.Companion.RUNNABLE_ENTITY_TYPE
import net.nemerosa.ontrack.model.events.EventFactory.Companion.RUN_INFO_RUN_TIME
import net.nemerosa.ontrack.model.events.EventFactory.Companion.RUN_INFO_SOURCE_TYPE
import net.nemerosa.ontrack.model.events.EventFactory.Companion.RUN_INFO_SOURCE_URI
import net.nemerosa.ontrack.model.events.EventFactory.Companion.RUN_INFO_TRIGGER_DATA
import net.nemerosa.ontrack.model.events.EventFactory.Companion.RUN_INFO_TRIGGER_TYPE
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_PROMOTION_LEVEL
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_VALIDATION_RUN_STATUS_COMMENT
import net.nemerosa.ontrack.model.events.EventFactory.Companion.UPDATE_VALIDATION_STAMP
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.Configuration
import net.nemerosa.ontrack.model.support.NameValue
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class EventFactoryImpl : EventFactory {

    private val types: MutableMap<String, EventType> =
        ConcurrentHashMap()

    init {
        register(NEW_PROJECT)
        register(UPDATE_PROJECT)
        register(DISABLE_PROJECT)
        register(ENABLE_PROJECT)
        register(DELETE_PROJECT)

        register(NEW_BRANCH)
        register(UPDATE_BRANCH)
        register(DISABLE_BRANCH)
        register(ENABLE_BRANCH)
        register(DELETE_BRANCH)

        register(NEW_BUILD)
        register(UPDATE_BUILD)
        register(UPDATE_BUILD_DISPLAY_NAME)
        register(DELETE_BUILD)
        register(NEW_BUILD_LINK)
        register(DELETE_BUILD_LINK)

        register(NEW_PROMOTION_LEVEL)
        register(IMAGE_PROMOTION_LEVEL)
        register(UPDATE_PROMOTION_LEVEL)
        register(DELETE_PROMOTION_LEVEL)
        register(REORDER_PROMOTION_LEVEL)

        register(NEW_VALIDATION_STAMP)
        register(IMAGE_VALIDATION_STAMP)
        register(UPDATE_VALIDATION_STAMP)
        register(DELETE_VALIDATION_STAMP)
        register(REORDER_VALIDATION_STAMP)

        register(NEW_PROMOTION_RUN)
        register(DELETE_PROMOTION_RUN)
        register(AUTO_PROMOTION_REVOKED)

        register(NEW_VALIDATION_RUN)
        register(NEW_VALIDATION_RUN_STATUS)
        register(UPDATE_VALIDATION_RUN_STATUS_COMMENT)
        register(DELETE_VALIDATION_RUN)
        register(UPDATE_VALIDATION_RUN_DATA)

        register(UPDATE_RUN_INFO)
        register(DELETE_RUN_INFO)

        register(PROPERTY_CHANGE)
        register(PROPERTY_DELETE)

        register(NEW_CONFIGURATION)
        register(UPDATE_CONFIGURATION)
        register(DELETE_CONFIGURATION)
    }

    final override fun register(eventType: EventType) {
        check(!types.containsKey(eventType.id)) {
            String.format(
                "Event with ID = %s is already registered.",
                eventType.id
            )
        }
        types[eventType.id] = eventType
    }

    override val eventTypes: Collection<EventType>
        get() = types.values

    override fun toEventType(id: String): EventType {
        val eventType = types[id]
        if (eventType != null) {
            return eventType
        } else {
            throw EventTypeNotFoundException(id)
        }
    }

    override fun newProject(project: Project): Event {
        return of(NEW_PROJECT).withProject(project).build()
    }

    override fun updateProject(project: Project): Event {
        return of(UPDATE_PROJECT).withProject(project).build()
    }

    override fun disableProject(project: Project): Event {
        return of(DISABLE_PROJECT).withProject(project).build()
    }

    override fun enableProject(project: Project): Event {
        return of(ENABLE_PROJECT).withProject(project).build()
    }

    override fun deleteProject(project: Project): Event {
        return of(DELETE_PROJECT)
            .with("PROJECT", project.name)
            .with("PROJECT_ID", project.id.toString())
            .build()
    }

    override fun newBranch(branch: Branch): Event {
        return of(NEW_BRANCH).withBranch(branch).build()
    }

    override fun updateBranch(branch: Branch): Event {
        return of(UPDATE_BRANCH).withBranch(branch).build()
    }

    override fun disableBranch(branch: Branch): Event {
        return of(DISABLE_BRANCH).withBranch(branch).build()
    }

    override fun enableBranch(branch: Branch): Event {
        return of(ENABLE_BRANCH).withBranch(branch).build()
    }

    override fun deleteBranch(branch: Branch): Event {
        return of(DELETE_BRANCH)
            .withProject(branch.project)
            .with("BRANCH", branch.name)
            .with("BRANCH_ID", branch.id.toString())
            .build()
    }

    override fun newBuild(build: Build): Event {
        return of(NEW_BUILD)
            .withBuild(build)
            .build()
    }

    override fun updateBuild(build: Build, previous: Build): Event {
        return of(UPDATE_BUILD)
            .withBuild(build)
            .withNoSignature()
            .with(PREVIOUS_BUILD_NAME, previous.name)
            .with(PREVIOUS_BUILD_DESCRIPTION, previous.description)
            .with(PREVIOUS_BUILD_CREATION, Time.store(previous.signature.time))
            .with(PREVIOUS_BUILD_CREATOR, previous.signature.user.name)
            .build()
    }

    override fun updateBuildDisplayName(build: Build, displayName: String): Event {
        return of(UPDATE_BUILD_DISPLAY_NAME)
            .withBuild(build)
            .with(DISPLAY_NAME, displayName)
            .build()
    }

    override fun deleteBuild(build: Build): Event {
        return of(DELETE_BUILD)
            .withBranch(build.branch)
            .with("BUILD", build.name)
            .with("BUILD_ID", build.id.toString())
            .build()
    }

    override fun newBuildLink(from: Build, to: Build, qualifier: String): Event =
        buildLinkEvent(NEW_BUILD_LINK, from, to, qualifier)

    override fun deleteBuildLink(from: Build, to: Build, qualifier: String): Event =
        buildLinkEvent(DELETE_BUILD_LINK, from, to, qualifier)

    /**
     * The source build is the main entity, the target build an extra one. The signature is the one of the
     * caller, not the one of the build.
     */
    private fun buildLinkEvent(eventType: EventType, from: Build, to: Build, qualifier: String): Event =
        of(eventType)
            .withBranch(from.branch)
            .with(from)
            .withExtra(to)
            .withExtra(to.branch)
            .with(QUALIFIER, qualifier)
            .build()

    override fun newPromotionLevel(promotionLevel: PromotionLevel): Event {
        return of(NEW_PROMOTION_LEVEL)
            .withPromotionLevel(promotionLevel)
            .build()
    }

    override fun imagePromotionLevel(promotionLevel: PromotionLevel): Event {
        return of(IMAGE_PROMOTION_LEVEL)
            .withPromotionLevel(promotionLevel)
            .build()
    }

    override fun updatePromotionLevel(promotionLevel: PromotionLevel): Event {
        return of(UPDATE_PROMOTION_LEVEL)
            .withPromotionLevel(promotionLevel)
            .build()
    }

    override fun deletePromotionLevel(promotionLevel: PromotionLevel): Event {
        return of(DELETE_PROMOTION_LEVEL)
            .withBranch(promotionLevel.branch)
            .with("PROMOTION_LEVEL", promotionLevel.name)
            .with("PROMOTION_LEVEL_ID", promotionLevel.id.toString())
            .build()
    }

    override fun reorderPromotionLevels(branch: Branch): Event {
        return of(REORDER_PROMOTION_LEVEL)
            .withBranch(branch)
            .build()
    }

    override fun newPromotionRun(promotionRun: PromotionRun): Event {
        return of(NEW_PROMOTION_RUN)
            .withPromotionRun(promotionRun)
            .build()
    }

    override fun deletePromotionRun(promotionRun: PromotionRun): Event {
        return of(DELETE_PROMOTION_RUN)
            .withBranch(promotionRun.build.branch)
            .with(promotionRun.build)
            .with("PROMOTION_RUN_ID", promotionRun.id.toString())
            .with(promotionRun.promotionLevel)
            .build()
    }

    override fun autoPromotionRevoked(build: Build, promotionLevel: PromotionLevel): Event {
        return of(AUTO_PROMOTION_REVOKED)
            .withBranch(build.branch)
            .with(build)
            .with(promotionLevel)
            .build()
    }

    override fun newValidationStamp(validationStamp: ValidationStamp): Event {
        return of(NEW_VALIDATION_STAMP)
            .withValidationStamp(validationStamp)
            .build()
    }

    override fun imageValidationStamp(validationStamp: ValidationStamp): Event {
        return of(IMAGE_VALIDATION_STAMP)
            .withValidationStamp(validationStamp)
            .build()
    }

    override fun updateValidationStamp(validationStamp: ValidationStamp): Event {
        return of(UPDATE_VALIDATION_STAMP)
            .withValidationStamp(validationStamp)
            .build()
    }

    override fun deleteValidationStamp(validationStamp: ValidationStamp): Event {
        return of(DELETE_VALIDATION_STAMP)
            .withBranch(validationStamp.branch)
            .with("VALIDATION_STAMP", validationStamp.name)
            .with("VALIDATION_STAMP_ID", validationStamp.id.toString())
            .build()
    }

    override fun reorderValidationStamps(branch: Branch): Event {
        return of(REORDER_VALIDATION_STAMP)
            .withBranch(branch)
            .build()
    }

    override fun newValidationRun(validationRun: ValidationRun): Event {
        return of(NEW_VALIDATION_RUN)
            .withValidationRun(validationRun)
            .withValidationRunStatus(validationRun.lastStatus.statusID)
            .build()
    }

    override fun newValidationRunStatus(validationRun: ValidationRun): Event {
        return of(NEW_VALIDATION_RUN_STATUS)
            .withValidationRun(validationRun)
            .withValidationRunStatus(validationRun.lastStatus.statusID)
            .build()
    }

    override fun updateValidationRunStatusComment(
        validationRun: ValidationRun,
        validationRunStatusId: ID,
        comment: String
    ): Event {
        return of(UPDATE_VALIDATION_RUN_STATUS_COMMENT)
            .withValidationRun(validationRun)
            .with(VALIDATION_RUN_STATUS_ID, validationRunStatusId.toString())
            .with(VALIDATION_RUN_STATUS_COMMENT, comment)
            .build()
    }

    /**
     * The run itself is not referenced since it is gone: only its build and stamp are.
     */
    override fun deleteValidationRun(validationRun: ValidationRun): Event =
        of(DELETE_VALIDATION_RUN)
            .withBuild(validationRun.build)
            .with(validationRun.validationStamp)
            .withNoSignature()
            .with(VALIDATION_RUN_ID, validationRun.id().toString())
            .with(VALIDATION_RUN_ORDER, validationRun.runOrder.toString())
            .withValidationRunStatus(validationRun.lastStatus.statusID)
            .build()

    override fun updateValidationRunData(validationRun: ValidationRun): Event =
        of(UPDATE_VALIDATION_RUN_DATA)
            .withValidationRun(validationRun)
            .withNoSignature()
            .with(VALIDATION_RUN_DATA_TYPE, validationRun.data?.descriptor?.id)
            .with(VALIDATION_RUN_DATA, validationRun.data?.data?.asJson()?.asJsonString())
            .build()

    override fun updateRunInfo(entity: RunnableEntity, runInfo: RunInfo): Event =
        runInfoEvent(UPDATE_RUN_INFO, entity)
            .with(RUN_INFO_SOURCE_TYPE, runInfo.sourceType)
            .with(RUN_INFO_SOURCE_URI, runInfo.sourceUri)
            .with(RUN_INFO_TRIGGER_TYPE, runInfo.triggerType)
            .with(RUN_INFO_TRIGGER_DATA, runInfo.triggerData)
            .with(RUN_INFO_RUN_TIME, runInfo.runTime?.toString())
            .build()

    override fun deleteRunInfo(entity: RunnableEntity): Event =
        runInfoEvent(DELETE_RUN_INFO, entity).build()

    /**
     * The runnable entity is the reference of the event, along with its build (and its validation stamp for
     * a validation run). The signature is the one of the caller, not the one of the entity.
     */
    private fun runInfoEvent(eventType: EventType, entity: RunnableEntity): Event.EventBuilder =
        when (entity) {
            is Build -> of(eventType).withBuild(entity)
            is ValidationRun -> of(eventType).withValidationRun(entity)
            else -> of(eventType)
        }
            .withRef(entity)
            .withNoSignature()
            .with(RUNNABLE_ENTITY_TYPE, entity.runnableEntityType.name)

    override fun <T> propertyChange(entity: ProjectEntity, propertyType: PropertyType<T>): Event {
        return of(PROPERTY_CHANGE)
            .withRef(entity)
            .with(
                "PROPERTY",
                NameValue(
                    propertyType.name,
                    propertyType.typeName
                )
            )
            .build()
    }

    override fun <T> propertyDelete(entity: ProjectEntity, propertyType: PropertyType<T>): Event {
        return of(PROPERTY_DELETE)
            .withRef(entity)
            .with(
                "PROPERTY",
                NameValue(
                    propertyType.name,
                    propertyType.typeName
                )
            )
            .build()
    }

    override fun <T : Configuration<T>> newConfiguration(configuration: T): Event {
        return of(NEW_CONFIGURATION)
            .with("CONFIGURATION", configuration.name)
            .build()
    }

    override fun <T : Configuration<T>> updateConfiguration(configuration: T): Event {
        return of(UPDATE_CONFIGURATION)
            .with("CONFIGURATION", configuration.name)
            .build()
    }

    override fun <T : Configuration<T>> deleteConfiguration(configuration: T): Event {
        return of(DELETE_CONFIGURATION)
            .with("CONFIGURATION", configuration.name)
            .with("CONFIGURATION_TYPE", configuration.javaClass.name)
            .build()
    }
}
