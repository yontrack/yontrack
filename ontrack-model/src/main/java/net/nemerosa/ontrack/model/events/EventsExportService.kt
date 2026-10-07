package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.exceptions.InputException
import java.io.OutputStream
import java.time.LocalDateTime

/**
 * Export of the events matching a [filter][EventFilter], as a file, newest first, capped by
 * `ontrack.config.events.export.max-rows`.
 *
 * Both formats share one [format version][FORMAT_VERSION]. Adding a field, or a column at the end
 * of the CSV, keeps it. Removing, renaming or retyping a field, or reordering the CSV columns,
 * bumps it.
 */
interface EventsExportService {

    /**
     * Whether the export of the events matching the [filter] would be truncated.
     *
     * Requires the [EventsAudit][net.nemerosa.ontrack.model.security.EventsAudit] global function.
     */
    fun exportInfo(filter: EventFilter): EventsExportInfo

    /**
     * Prepares the export of the events matching the [filter].
     *
     * Requires the [EventsAudit][net.nemerosa.ontrack.model.security.EventsAudit] global function,
     * checked now, before anything is written: the export can then be written in the
     * [response][EventsExport.writeTo].
     */
    fun export(filter: EventFilter, format: EventsExportFormat): EventsExport

    companion object {
        /**
         * Version of the format of the exports, CSV and JSON alike.
         */
        const val FORMAT_VERSION = 1

        /**
         * Value of the `id` column of the last CSV row, when the export is truncated.
         */
        const val TRUNCATED_MARKER = "TRUNCATED"
    }
}

/**
 * Format of an export of the events.
 *
 * @property id Value of the `format` parameter, and extension of the file
 * @property contentType Content type of the file
 */
enum class EventsExportFormat(
    val id: String,
    val contentType: String,
) {
    CSV("csv", "text/csv; charset=UTF-8"),
    JSON("json", "application/json");

    companion object {
        /**
         * Format from its [id].
         *
         * @throws EventsExportFormatException When there is no such format
         */
        fun parse(value: String): EventsExportFormat =
            entries.firstOrNull { it.id == value } ?: throw EventsExportFormatException(value)
    }
}

/**
 * Unknown format of an export of the events.
 */
class EventsExportFormatException(format: String) : InputException(
    if (format.isBlank()) {
        "Missing format for the export of the events. Use one of: ${EventsExportFormat.entries.joinToString { it.id }}."
    } else {
        "Unknown format for the export of the events: $format. Use one of: ${EventsExportFormat.entries.joinToString { it.id }}."
    }
)

/**
 * What the export of the events matching a filter would hold.
 */
@APIDescription("What the export of the events matching a filter would hold")
data class EventsExportInfo(
    @APIDescription("Maximum number of events in an export")
    val maxRows: Int,
    @APIDescription("Whether more events than `maxRows` match the filter: the export then holds the `maxRows` most recent ones only")
    val truncated: Boolean,
)

/**
 * Export of the events, ready to be written.
 */
interface EventsExport {

    /**
     * Format of the export
     */
    val format: EventsExportFormat

    /**
     * Maximum number of events, and whether the export is truncated
     */
    val info: EventsExportInfo

    /**
     * Time of the export (UTC)
     */
    val exportedAt: LocalDateTime

    /**
     * Writes the export, reading the events in chunks.
     */
    fun writeTo(output: OutputStream)
}
