package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import tools.jackson.databind.JsonNode

/**
 * Reads a report of one format into findings.
 *
 * Every format is parsed by Yontrack itself, so that every client — the CLI, the KDSL, a direct
 * GraphQL call — benefits from one implementation. A parser is a Spring component, found by its
 * [format]: adding a format is adding a parser.
 */
interface FindingsReportParser {

    /**
     * Value of the `format` argument of `validateBuildWithFindings` for this parser.
     */
    val format: String

    /**
     * Reads a report.
     *
     * @param report Report, as JSON
     * @param scanner Scanner given by the caller, taking precedence over the report's
     * @param kind Kind given by the caller, taking precedence over the report's
     * @return The findings of the report, with the scanner and the kind resolved
     * @throws FindingsReportFormatException When the report is not valid
     */
    fun parse(report: JsonNode, scanner: String?, kind: FindingKind?): ParsedFindingsReport
}
