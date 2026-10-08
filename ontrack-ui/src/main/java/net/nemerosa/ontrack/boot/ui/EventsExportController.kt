package net.nemerosa.ontrack.boot.ui

import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventsExportFormat
import net.nemerosa.ontrack.model.events.EventsExportService
import net.nemerosa.ontrack.model.exceptions.InputException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Export of the events matching a filter, as a CSV or JSON file ([EventsExportService]).
 */
@RestController
@RequestMapping("/rest/admin/events")
class EventsExportController(
    private val eventsExportService: EventsExportService,
) {

    /**
     * Export of the events matching a filter, newest first, as an attachment. The filter is the
     * one of the `events` GraphQL query.
     *
     * Requires the events audit function. The export is written as it is read, without being
     * buffered: its headers say, before its content, whether it is truncated.
     *
     * @param format `csv` or `json`
     * @param from Events at or after this time, as ISO-8601 - UTC when there is no offset
     * @param to Events at or before this time, as ISO-8601 - UTC when there is no offset
     * @param user Case-insensitive prefix of the name of the user who posted the event
     * @param eventTypes IDs of the event types to keep, repeated or separated by commas
     * @param project Name of a project, matching the event's project or its extra project
     * @param actor `agent`, `human`, or the identifier of one agent, `<slug>[agent]`
     */
    @GetMapping("export")
    fun export(
        @RequestParam(required = false) format: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) user: String?,
        @RequestParam(required = false) eventTypes: List<String>?,
        @RequestParam(required = false) project: String?,
        @RequestParam(required = false) actor: String?,
        response: HttpServletResponse,
    ) {
        val exportFormat = EventsExportFormat.parse(format ?: "")
        val filter = EventFilter(
            from = parseTime("from", from),
            to = parseTime("to", to),
            user = user?.takeIf { it.isNotBlank() },
            eventTypes = eventTypes?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
            project = project?.takeIf { it.isNotBlank() },
            actor = actor?.takeIf { it.isNotBlank() },
        )
        // Checks the access, before anything is written
        val export = eventsExportService.export(filter, exportFormat)
        response.status = HttpServletResponse.SC_OK
        response.contentType = export.format.contentType
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("yontrack-events-${export.exportedAt.format(FILE_NAME_TIME)}.${export.format.id}")
                .build()
                .toString()
        )
        response.setHeader(HEADER_FORMAT_VERSION, EventsExportService.FORMAT_VERSION.toString())
        response.setHeader(HEADER_TRUNCATED, export.info.truncated.toString())
        export.writeTo(response.outputStream)
        response.flushBuffer()
    }

    /**
     * Time of the filter: an ISO-8601 local date time, in UTC, or with an offset.
     */
    private fun parseTime(name: String, value: String?): LocalDateTime? =
        value?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.ofInstant(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(it)), ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    throw EventsExportTimeException(name, it)
                }
            }
        }

    companion object {
        /**
         * Header giving the version of the format of the export
         */
        const val HEADER_FORMAT_VERSION = "X-Yontrack-Export-Format-Version"

        /**
         * Header saying whether the export is truncated
         */
        const val HEADER_TRUNCATED = "X-Yontrack-Export-Truncated"

        private val FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

/**
 * Time of the filter of an export of the events which cannot be parsed.
 */
class EventsExportTimeException(name: String, value: String) : InputException(
    "The `$name` time of the export of the events is not an ISO-8601 date time: $value"
)
