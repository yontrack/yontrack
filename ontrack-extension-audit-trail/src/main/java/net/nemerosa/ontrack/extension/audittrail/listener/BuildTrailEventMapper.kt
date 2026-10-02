package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.build
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.canonicalValue
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.claimed
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationRun
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationStamp
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.PropertyType
import net.nemerosa.ontrack.model.structure.RunnableEntityType
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode

/**
 * Entries of the changes of a build itself: its creation and edition, its properties, its links to
 * other builds, and the run info of the build or of its validation runs.
 */
@Component
class BuildTrailEventMapper(
    private val propertyService: PropertyService,
) : TrailEventMapper {

    override val eventTypes: Set<String> = setOf(
        EventFactory.NEW_BUILD.id,
        EventFactory.UPDATE_BUILD.id,
        EventFactory.PROPERTY_CHANGE.id,
        EventFactory.PROPERTY_DELETE.id,
        EventFactory.NEW_BUILD_LINK.id,
        EventFactory.DELETE_BUILD_LINK.id,
        EventFactory.UPDATE_RUN_INFO.id,
        EventFactory.DELETE_RUN_INFO.id,
    )

    override fun map(event: Event): List<TrailEntryRequest> =
        when (event.eventType.id) {
            EventFactory.NEW_BUILD.id -> buildCreated(event)
            EventFactory.UPDATE_BUILD.id -> buildUpdated(event)
            EventFactory.PROPERTY_CHANGE.id -> propertySet(event)
            EventFactory.PROPERTY_DELETE.id -> propertyDeleted(event)
            EventFactory.NEW_BUILD_LINK.id -> link(event, TrailEntryTypes.LINK_ADDED)
            EventFactory.DELETE_BUILD_LINK.id -> link(event, TrailEntryTypes.LINK_REMOVED)
            EventFactory.UPDATE_RUN_INFO.id -> runInfoSet(event)
            EventFactory.DELETE_RUN_INFO.id -> runInfoDeleted(event)
            else -> emptyList()
        }

    private fun buildCreated(event: Event): List<TrailEntryRequest> {
        val build: Build = event.getEntity(ProjectEntityType.BUILD)
        return listOf(
            TrailEntryRequest(
                build = build,
                type = TrailEntryTypes.BUILD_CREATED,
                payload = payload(
                    "build" to build(build),
                    "description" to build.description,
                    "claimed" to claimed(build.signature),
                ),
            )
        )
    }

    private fun buildUpdated(event: Event): List<TrailEntryRequest> {
        val build: Build = event.getEntity(ProjectEntityType.BUILD)
        return listOf(
            TrailEntryRequest(
                build = build,
                type = TrailEntryTypes.BUILD_UPDATED,
                payload = payload(
                    "old" to mapOf(
                        "name" to event.value(EventFactory.PREVIOUS_BUILD_NAME),
                        "description" to event.value(EventFactory.PREVIOUS_BUILD_DESCRIPTION),
                        "creation" to Time.fromStorage(event.value(EventFactory.PREVIOUS_BUILD_CREATION))
                            ?.let { TrailHashFormatV1.formatTime(it) },
                        "creator" to event.value(EventFactory.PREVIOUS_BUILD_CREATOR),
                    ),
                    "new" to mapOf(
                        "name" to build.name,
                        "description" to build.description,
                        "creation" to TrailHashFormatV1.formatTime(build.signature.time),
                        "creator" to build.signature.user.name,
                    ),
                ),
            )
        )
    }

    /**
     * Only the properties of builds are recorded.
     */
    private fun propertySet(event: Event): List<TrailEntryRequest> {
        val build = event.propertyBuild() ?: return emptyList()
        val propertyTypeName = event.getValue(PROPERTY)
        val property = propertyService.getProperty<Any>(build, propertyTypeName)
        val value: JsonNode = property.value
            ?.let { canonicalValue(property.type.storageOf(it)) }
            ?: NullNode.instance
        return listOf(
            TrailEntryRequest(
                build = build,
                type = TrailEntryTypes.PROPERTY_SET,
                payload = payload(
                    "propertyType" to propertyTypeName,
                    "value" to value,
                ),
            )
        )
    }

    private fun propertyDeleted(event: Event): List<TrailEntryRequest> {
        val build = event.propertyBuild() ?: return emptyList()
        return listOf(
            TrailEntryRequest(
                build = build,
                type = TrailEntryTypes.PROPERTY_DELETED,
                payload = payload(
                    "propertyType" to event.getValue(PROPERTY),
                ),
            )
        )
    }

    private fun Event.propertyBuild(): Build? =
        if (ref == ProjectEntityType.BUILD) {
            getEntity(ProjectEntityType.BUILD)
        } else {
            null
        }

    @Suppress("UNCHECKED_CAST")
    private fun PropertyType<*>.storageOf(value: Any): JsonNode =
        (this as PropertyType<Any>).forStorage(value)

    private fun link(event: Event, type: String): List<TrailEntryRequest> {
        val build: Build = event.getEntity(ProjectEntityType.BUILD)
        val target = event.extraEntities[ProjectEntityType.BUILD] as Build
        return listOf(
            TrailEntryRequest(
                build = build,
                type = type,
                payload = payload(
                    "target" to build(target),
                    "qualifier" to event.getValue(EventFactory.QUALIFIER),
                ),
            )
        )
    }

    private fun runInfoSet(event: Event): List<TrailEntryRequest> {
        val build: Build = event.getEntity(ProjectEntityType.BUILD)
        return listOf(
            TrailEntryRequest(
                build = build,
                type = TrailEntryTypes.RUN_INFO_SET,
                payload = payload(
                    "runnable" to event.runnable(),
                    "runInfo" to mapOf(
                        "sourceType" to event.value(EventFactory.RUN_INFO_SOURCE_TYPE),
                        "sourceUri" to event.value(EventFactory.RUN_INFO_SOURCE_URI),
                        "triggerType" to event.value(EventFactory.RUN_INFO_TRIGGER_TYPE),
                        "triggerData" to event.value(EventFactory.RUN_INFO_TRIGGER_DATA),
                        "runTime" to event.value(EventFactory.RUN_INFO_RUN_TIME)?.toInt(),
                    ),
                ),
            )
        )
    }

    private fun runInfoDeleted(event: Event): List<TrailEntryRequest> {
        val build: Build = event.getEntity(ProjectEntityType.BUILD)
        return listOf(
            TrailEntryRequest(
                build = build,
                type = TrailEntryTypes.RUN_INFO_DELETED,
                payload = payload(
                    "runnable" to event.runnable(),
                ),
            )
        )
    }

    /**
     * The build, or the validation run of the build, whose run info changed.
     */
    private fun Event.runnable(): Map<String, Any> =
        when (getValue(EventFactory.RUNNABLE_ENTITY_TYPE)) {
            RunnableEntityType.validation_run.name -> {
                val run: ValidationRun = getEntity(ProjectEntityType.VALIDATION_RUN)
                mapOf(
                    "type" to RunnableEntityType.validation_run.name,
                    "validationStamp" to validationStamp(run.validationStamp),
                    "validationRun" to validationRun(run),
                )
            }

            else -> mapOf("type" to RunnableEntityType.build.name)
        }

    companion object {
        /**
         * Name of the value of the property events holding the FQCN of the property type.
         */
        private const val PROPERTY = "PROPERTY"
    }
}
