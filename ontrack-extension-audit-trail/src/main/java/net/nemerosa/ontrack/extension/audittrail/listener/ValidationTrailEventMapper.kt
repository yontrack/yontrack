package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.claimed
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.sha256
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationRun
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationStamp
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

/**
 * Entries of the validations of a build: runs, their statuses, comments and data, and their
 * deletion.
 *
 * The data of a run is never written in the trail, only its type and the SHA-256 of its canonical
 * form (see [TrailPayloads.sha256]), the data being serialized as the events serialize it.
 */
@Component
class ValidationTrailEventMapper : TrailEventMapper {

    override val eventTypes: Set<String> = setOf(
        EventFactory.NEW_VALIDATION_RUN.id,
        EventFactory.NEW_VALIDATION_RUN_STATUS.id,
        EventFactory.UPDATE_VALIDATION_RUN_STATUS_COMMENT.id,
        EventFactory.UPDATE_VALIDATION_RUN_DATA.id,
        EventFactory.DELETE_VALIDATION_RUN.id,
    )

    override fun map(event: Event): List<TrailEntryRequest> =
        when (event.eventType.id) {
            EventFactory.NEW_VALIDATION_RUN.id -> {
                val run = event.run()
                run.entry(
                    TrailEntryTypes.VALIDATION_RUN,
                    "status" to run.lastStatus.statusID.id,
                    "data" to run.dataRef(),
                    "claimed" to claimed(run.lastStatus.signature),
                )
            }

            EventFactory.NEW_VALIDATION_RUN_STATUS.id -> {
                val run = event.run()
                run.entry(
                    TrailEntryTypes.VALIDATION_STATUS,
                    "status" to run.lastStatus.statusID.id,
                    "description" to run.lastStatus.description,
                    "claimed" to claimed(run.lastStatus.signature),
                )
            }

            EventFactory.UPDATE_VALIDATION_RUN_STATUS_COMMENT.id -> event.run().entry(
                TrailEntryTypes.VALIDATION_COMMENT,
                "validationRunStatusId" to event.value(EventFactory.VALIDATION_RUN_STATUS_ID)?.toInt(),
                "comment" to event.value(EventFactory.VALIDATION_RUN_STATUS_COMMENT),
            )

            EventFactory.UPDATE_VALIDATION_RUN_DATA.id -> {
                val run = event.run()
                run.entry(
                    TrailEntryTypes.VALIDATION_DATA,
                    "data" to run.dataRef(),
                )
            }

            EventFactory.DELETE_VALIDATION_RUN.id -> {
                // The run is gone: the event carries what identified it
                val build: Build = event.getEntity(ProjectEntityType.BUILD)
                val validationStamp: ValidationStamp = event.getEntity(ProjectEntityType.VALIDATION_STAMP)
                listOf(
                    TrailEntryRequest(
                        build = build,
                        type = TrailEntryTypes.VALIDATION_DELETED,
                        payload = payload(
                            "validationStamp" to validationStamp(validationStamp),
                            "validationRun" to validationRun(
                                event.getIntValue(EventFactory.VALIDATION_RUN_ID),
                                event.getIntValue(EventFactory.VALIDATION_RUN_ORDER),
                            ),
                            "status" to event.value(STATUS),
                        ),
                    )
                )
            }

            else -> emptyList()
        }

    private fun Event.run(): ValidationRun = getEntity(ProjectEntityType.VALIDATION_RUN)

    private fun ValidationRun.entry(type: String, vararg properties: Pair<String, Any?>) = listOf(
        TrailEntryRequest(
            build = build,
            type = type,
            payload = payload(
                "validationStamp" to validationStamp(validationStamp),
                "validationRun" to validationRun(this),
                *properties,
            ),
        )
    )

    /**
     * Type of the data of the run and SHA-256 of its canonical form, `null` without data.
     */
    private fun ValidationRun.dataRef(): Map<String, Any>? = data?.let { data ->
        mapOf(
            "type" to data.descriptor.id,
            "sha256" to sha256(data.data.asJson()),
        )
    }

    companion object {
        /**
         * Name of the value of the validation events holding the ID of the status of the run.
         */
        private const val STATUS = "STATUS"
    }
}
