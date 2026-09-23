package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Parser of the neutral format of Yontrack, the `findings` format ([FindingsReport]).
 *
 * Strict: an unknown field is rejected wherever it is, and so is a missing required field, a
 * value of the wrong type or an unknown enumerated value. All the errors of a report are
 * collected before failing. An error names the offending field, never its value.
 */
@Component
class NeutralFindingsReportParser : FindingsReportParser {

    override val format: String = FORMAT

    override fun parse(report: JsonNode, scanner: String?, kind: FindingKind?): ParsedFindingsReport {
        val reader = Reader()
        val parsed = reader.readReport(report, scanner, kind)
        if (reader.errors.isNotEmpty() || parsed == null) {
            throw FindingsReportFormatException(FORMAT, reader.errors)
        }
        return parsed
    }

    private class Reader {

        val errors = mutableListOf<String>()

        fun readReport(node: JsonNode, scanner: String?, kind: FindingKind?): ParsedFindingsReport? {
            if (!node.isObject) {
                errors += "the report must be a JSON object"
                return null
            }
            checkFields(node, "", ROOT_FIELDS)

            val reportScanner = optionalString(node, "", FindingsReport::scanner.name, notBlank = true)
            val actualScanner = scanner?.takeIf { it.isNotBlank() } ?: reportScanner
            if (actualScanner == null && isAbsent(node, FindingsReport::scanner.name)) {
                errors += "`scanner` is required, in the report or as an argument"
            }

            val reportKind = optionalEnum<FindingKind>(node, "", FindingsReport::kind.name)
            val actualKind = kind ?: reportKind
            if (actualKind == null && isAbsent(node, FindingsReport::kind.name)) {
                errors += "`kind` is required, in the report or as an argument"
            }

            val findings = readFindings(node)

            return if (actualScanner != null && actualKind != null && findings != null) {
                ParsedFindingsReport(
                    scanner = actualScanner,
                    kind = actualKind,
                    findings = findings,
                )
            } else {
                null
            }
        }

        private fun readFindings(node: JsonNode): List<FindingsReportEntry>? {
            val field = FindingsReport::findings.name
            val findings = node.get(field)
            return if (findings == null || findings.isNull) {
                errors += "`$field`: required"
                null
            } else if (!findings.isArray) {
                errors += "`$field`: must be an array"
                null
            } else {
                findings.values().mapIndexedNotNull { index, entry ->
                    readEntry(entry, "$field[$index]")
                }
            }
        }

        private fun readEntry(node: JsonNode, path: String): FindingsReportEntry? {
            if (!node.isObject) {
                errors += "`$path`: must be an object"
                return null
            }
            checkFields(node, path, ENTRY_FIELDS)
            val externalId = requiredString(node, path, FindingsReportEntry::externalId.name, notBlank = true)
            val location = requiredString(node, path, FindingsReportEntry::location.name, notBlank = false)
            val severity = requiredEnum<FindingSeverity>(node, path, FindingsReportEntry::severity.name)
            val rawSeverity = optionalString(node, path, FindingsReportEntry::rawSeverity.name)
            val title = requiredString(node, path, FindingsReportEntry::title.name, notBlank = true)
            val url = optionalString(node, path, FindingsReportEntry::url.name)
            val fixedVersion = optionalString(node, path, FindingsReportEntry::fixedVersion.name)
            val installedVersion = optionalString(node, path, FindingsReportEntry::installedVersion.name)
            val acceptance = readAcceptance(node, path)
            return if (externalId != null && location != null && severity != null && title != null) {
                FindingsReportEntry(
                    externalId = externalId,
                    location = location,
                    severity = severity,
                    rawSeverity = rawSeverity,
                    title = title,
                    url = url,
                    fixedVersion = fixedVersion,
                    installedVersion = installedVersion,
                    acceptance = acceptance,
                )
            } else {
                null
            }
        }

        private fun readAcceptance(parent: JsonNode, parentPath: String): FindingsReportAcceptance? {
            val field = FindingsReportEntry::acceptance.name
            val node = parent.get(field)
            if (node == null || node.isNull) {
                return null
            }
            val path = "$parentPath.$field"
            if (!node.isObject) {
                errors += "`$path`: must be an object"
                return null
            }
            checkFields(node, path, ACCEPTANCE_FIELDS)
            val statement = requiredString(node, path, FindingsReportAcceptance::statement.name, notBlank = false)
            val expiresAt = optionalDate(node, path, FindingsReportAcceptance::expiresAt.name)
            val source = requiredString(node, path, FindingsReportAcceptance::source.name, notBlank = false)
            return if (statement != null && source != null) {
                FindingsReportAcceptance(
                    statement = statement,
                    expiresAt = expiresAt,
                    source = source,
                )
            } else {
                null
            }
        }

        private fun checkFields(node: JsonNode, path: String, allowed: Set<String>) {
            node.properties().forEach { (name, _) ->
                if (name !in allowed) {
                    errors += "`${path(path, name)}`: unknown field"
                }
            }
        }

        private fun isAbsent(node: JsonNode, field: String): Boolean =
            node.get(field).let { it == null || it.isNull }

        private fun requiredString(node: JsonNode, path: String, field: String, notBlank: Boolean): String? {
            val value = node.get(field)
            return if (value == null || value.isNull) {
                errors += "`${path(path, field)}`: required"
                null
            } else {
                string(value, path, field, notBlank)
            }
        }

        private fun optionalString(node: JsonNode, path: String, field: String, notBlank: Boolean = false): String? {
            val value = node.get(field)
            return if (value == null || value.isNull) {
                null
            } else {
                string(value, path, field, notBlank)
            }
        }

        private fun string(value: JsonNode, path: String, field: String, notBlank: Boolean): String? =
            if (!value.isString) {
                errors += "`${path(path, field)}`: must be a string"
                null
            } else {
                val text = value.stringValue()
                if (notBlank && text.isBlank()) {
                    errors += "`${path(path, field)}`: must not be blank"
                    null
                } else {
                    text
                }
            }

        private inline fun <reified E : Enum<E>> requiredEnum(node: JsonNode, path: String, field: String): E? {
            val value = node.get(field)
            return if (value == null || value.isNull) {
                errors += "`${path(path, field)}`: required"
                null
            } else {
                enum<E>(value, path, field)
            }
        }

        private inline fun <reified E : Enum<E>> optionalEnum(node: JsonNode, path: String, field: String): E? {
            val value = node.get(field)
            return if (value == null || value.isNull) {
                null
            } else {
                enum<E>(value, path, field)
            }
        }

        private inline fun <reified E : Enum<E>> enum(value: JsonNode, path: String, field: String): E? {
            val values = enumValues<E>()
            val result = if (value.isString) {
                values.firstOrNull { it.name == value.stringValue() }
            } else {
                null
            }
            if (result == null) {
                errors += "`${path(path, field)}`: must be one of ${values.joinToString(", ") { it.name }}"
            }
            return result
        }

        private fun optionalDate(node: JsonNode, path: String, field: String): LocalDate? {
            val value = optionalString(node, path, field) ?: return null
            return try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                errors += "`${path(path, field)}`: must be an ISO date (yyyy-MM-dd)"
                null
            }
        }

        private fun path(path: String, field: String) = if (path.isEmpty()) field else "$path.$field"
    }

    companion object {
        /**
         * Name of the neutral format
         */
        const val FORMAT = "findings"

        private val ROOT_FIELDS = setOf(
            FindingsReport::scanner.name,
            FindingsReport::kind.name,
            FindingsReport::findings.name,
        )

        private val ENTRY_FIELDS = setOf(
            FindingsReportEntry::externalId.name,
            FindingsReportEntry::location.name,
            FindingsReportEntry::severity.name,
            FindingsReportEntry::rawSeverity.name,
            FindingsReportEntry::title.name,
            FindingsReportEntry::url.name,
            FindingsReportEntry::fixedVersion.name,
            FindingsReportEntry::installedVersion.name,
            FindingsReportEntry::acceptance.name,
        )

        private val ACCEPTANCE_FIELDS = setOf(
            FindingsReportAcceptance::statement.name,
            FindingsReportAcceptance::expiresAt.name,
            FindingsReportAcceptance::source.name,
        )
    }
}
