package net.nemerosa.ontrack.service.events

import com.opencsv.CSVWriter
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.EventsExport
import net.nemerosa.ontrack.model.events.EventsExportFormat
import net.nemerosa.ontrack.model.events.EventsExportInfo
import net.nemerosa.ontrack.model.events.EventsExportService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.EventRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.core.JsonGenerator
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Export of the events, read from the repository in chunks of [CHUNK_SIZE] events, by keyset.
 *
 * Not transactional: each chunk is read on its own, and the export only sees the events which
 * existed when it was [prepared][export].
 */
@Service
class EventsExportServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val eventFactory: EventFactory,
    private val eventRepository: EventRepository,
    private val eventTemplatingService: EventTemplatingService,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : EventsExportService {

    private val logger: Logger = LoggerFactory.getLogger(EventsExportServiceImpl::class.java)

    private val maxRows: Int get() = ontrackConfigProperties.events.export.maxRows

    override fun exportInfo(filter: EventFilter): EventsExportInfo {
        securityService.checkGlobalFunction(EventsAudit::class.java)
        val maxRows = this.maxRows
        return EventsExportInfo(
            maxRows = maxRows,
            truncated = eventRepository.hasEventsBeyond(filter, beforeId = null, offset = maxRows),
        )
    }

    override fun export(filter: EventFilter, format: EventsExportFormat): EventsExport {
        securityService.checkGlobalFunction(EventsAudit::class.java)
        val maxRows = this.maxRows
        // The export only sees the events which exist now
        val beforeId = (eventRepository.getLastEventId() ?: 0) + 1
        val info = EventsExportInfo(
            maxRows = maxRows,
            truncated = eventRepository.hasEventsBeyond(filter, beforeId = beforeId, offset = maxRows),
        )
        return EventsExportImpl(
            filter = filter,
            format = format,
            info = info,
            exportedAt = Time.now(),
            beforeId = beforeId,
        )
    }

    private inner class EventsExportImpl(
        private val filter: EventFilter,
        override val format: EventsExportFormat,
        override val info: EventsExportInfo,
        override val exportedAt: LocalDateTime,
        private val beforeId: Int,
    ) : EventsExport {

        override fun writeTo(output: OutputStream) {
            val writer: EventsExportWriter = when (format) {
                EventsExportFormat.CSV -> CsvEventsExportWriter(output)
                EventsExportFormat.JSON -> JsonEventsExportWriter(output)
            }
            // The auditor sees all the events, whatever the project ACLs
            securityService.asAdmin {
                writer.start(this)
                rows().forEach { writer.row(it) }
                writer.end(this)
            }
        }

        /**
         * Rows of the export, newest first, read chunk by chunk, up to the maximum.
         */
        private fun rows(): Sequence<EventsExportRow> = sequence {
            val entities = EntityCache()
            var remaining = info.maxRows
            var lastId = beforeId
            while (remaining > 0) {
                val size = minOf(CHUNK_SIZE, remaining)
                val events = eventRepository.findEventsBefore(
                    filter = filter,
                    beforeId = lastId,
                    size = size,
                    entityLoader = entities::load,
                    eventTypeLoader = { eventFactory.toEventType(it) },
                )
                yieldAll(events.map { toRow(it) })
                remaining -= events.size
                if (events.size < size) break
                lastId = events.last().id
            }
        }

        val filterValues: Map<String, Any?>
            get() = linkedMapOf(
                "from" to filter.from?.let(::formatTime),
                "to" to filter.to?.let(::formatTime),
                "user" to filter.user,
                "eventTypes" to filter.eventTypes,
                "project" to filter.project,
            )
    }

    /**
     * Loading the entities of the events, keeping the most recent ones: the events of an export
     * often refer to the same project, branch or build.
     */
    private inner class EntityCache {
        private val cache = object : LinkedHashMap<Pair<ProjectEntityType, Int>, ProjectEntity>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<ProjectEntityType, Int>, ProjectEntity>?): Boolean =
                size > ENTITY_CACHE_SIZE
        }

        fun load(type: ProjectEntityType, id: ID): ProjectEntity =
            cache.getOrPut(type to id.get()) {
                type.getEntityFn(structureService).apply(id)
            }
    }

    private fun toRow(event: Event) = EventsExportRow(
        id = event.id,
        time = event.signature?.time?.let(::formatTime),
        user = event.signature?.user?.name,
        eventType = event.eventType.id,
        message = renderMessage(event),
        entities = event.entities.mapValues { (_, entity) -> entity.exportName() },
        extraEntities = event.extraEntities.mapValues { (_, entity) -> entity.exportName() },
        ref = event.ref?.name,
        values = event.values.mapValuesTo(LinkedHashMap()) { (_, nameValue) -> nameValue.value },
    )

    private fun renderMessage(event: Event): String =
        try {
            eventTemplatingService.renderEvent(
                event = event,
                context = emptyMap(),
                template = null,
                renderer = PlainEventRenderer.INSTANCE,
            )
        } catch (any: Exception) {
            // One event which cannot be rendered does not stop the export
            logger.warn("Cannot render the message of the event ${event.id}", any)
            "Cannot render the message: ${any.message}"
        }

    /**
     * Name of a project entity in an export: its name, or its ID for a run.
     */
    private fun ProjectEntity.exportName(): String = when (this) {
        is Project -> name
        is Branch -> name
        is Build -> name
        is PromotionLevel -> name
        is ValidationStamp -> name
        else -> id().toString()
    }

    companion object {
        /**
         * Number of events read at once
         */
        const val CHUNK_SIZE = 500

        /**
         * Number of entities kept while exporting
         */
        const val ENTITY_CACHE_SIZE = 1000

        /**
         * Columns of the entities, in the order of the export
         */
        private val ENTITY_COLUMNS = listOf(
            ProjectEntityType.PROJECT to "project",
            ProjectEntityType.BRANCH to "branch",
            ProjectEntityType.BUILD to "build",
            ProjectEntityType.PROMOTION_LEVEL to "promotionLevel",
            ProjectEntityType.VALIDATION_STAMP to "validationStamp",
            ProjectEntityType.PROMOTION_RUN to "promotionRun",
            ProjectEntityType.VALIDATION_RUN to "validationRun",
        )

        /**
         * Columns of the version 1 of the format, CSV and JSON alike
         */
        val COLUMNS: List<String> =
            listOf("id", "time", "user", "eventType", "message") +
                    ENTITY_COLUMNS.map { it.second } +
                    ENTITY_COLUMNS.map { (_, name) -> "x${name.replaceFirstChar { it.uppercase() }}" } +
                    listOf("ref", "values")

        private val json = ObjectMapperFactory.create()

        /**
         * ISO-8601 time, in UTC
         */
        private fun formatTime(time: LocalDateTime): String =
            DateTimeFormatter.ISO_INSTANT.format(time.toInstant(ZoneOffset.UTC))
    }

    /**
     * An event, as exported.
     */
    private class EventsExportRow(
        val id: Int,
        val time: String?,
        val user: String?,
        val eventType: String,
        val message: String,
        val entities: Map<ProjectEntityType, String>,
        val extraEntities: Map<ProjectEntityType, String>,
        val ref: String?,
        val values: Map<String, String>,
    ) {
        /**
         * Values of the row, in the order of the [columns][COLUMNS], but the values
         */
        fun scalars(): List<Pair<String, Any?>> =
            listOf(
                "id" to id,
                "time" to time,
                "user" to user,
                "eventType" to eventType,
                "message" to message,
            ) + ENTITY_COLUMNS.map { (type, name) -> name to entities[type] } +
                    ENTITY_COLUMNS.map { (type, name) -> "x${name.replaceFirstChar { it.uppercase() }}" to extraEntities[type] } +
                    listOf("ref" to ref)
    }

    private interface EventsExportWriter {
        fun start(export: EventsExportImpl)
        fun row(row: EventsExportRow)
        fun end(export: EventsExportImpl)
    }

    private class CsvEventsExportWriter(output: OutputStream) : EventsExportWriter {

        private val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
        private val csv = CSVWriter(writer)

        override fun start(export: EventsExportImpl) {
            csv.writeNext(COLUMNS.toTypedArray(), false)
        }

        override fun row(row: EventsExportRow) {
            val cells = row.scalars().map { (_, value) -> value?.toString() ?: "" } +
                    json.writeValueAsString(row.values)
            csv.writeNext(cells.toTypedArray(), false)
        }

        override fun end(export: EventsExportImpl) {
            if (export.info.truncated) {
                val marker = COLUMNS.map { column ->
                    when (column) {
                        "id" -> EventsExportService.TRUNCATED_MARKER
                        "message" -> "Export limited to ${export.info.maxRows} events: narrow the filter"
                        else -> ""
                    }
                }
                csv.writeNext(marker.toTypedArray(), false)
            }
            csv.flush()
        }
    }

    private class JsonEventsExportWriter(output: OutputStream) : EventsExportWriter {

        private val generator: JsonGenerator = json.createGenerator(output)

        override fun start(export: EventsExportImpl) {
            generator.writeStartObject()
            // The version first
            generator.writeNumberProperty("formatVersion", EventsExportService.FORMAT_VERSION)
            generator.writeStringProperty("exportedAt", formatTime(export.exportedAt))
            generator.writeName("filter")
            generator.writeStartObject()
            export.filterValues.forEach { (name, value) ->
                generator.writePOJOProperty(name, value)
            }
            generator.writeEndObject()
            generator.writeNumberProperty("maxRows", export.info.maxRows)
            generator.writeBooleanProperty("truncated", export.info.truncated)
            // The events last
            generator.writeName("events")
            generator.writeStartArray()
        }

        override fun row(row: EventsExportRow) {
            generator.writeStartObject()
            row.scalars().forEach { (name, value) ->
                generator.writePOJOProperty(name, value)
            }
            generator.writeName("values")
            generator.writeStartObject()
            row.values.forEach { (name, value) ->
                generator.writeStringProperty(name, value)
            }
            generator.writeEndObject()
            generator.writeEndObject()
        }

        override fun end(export: EventsExportImpl) {
            generator.writeEndArray()
            generator.writeEndObject()
            generator.flush()
        }
    }
}
