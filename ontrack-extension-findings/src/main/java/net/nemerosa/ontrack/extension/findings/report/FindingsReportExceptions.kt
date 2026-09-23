package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.common.UserException

/**
 * A report which cannot be read. Nothing is created: the CI step posting it fails.
 */
class FindingsReportFormatException(
    format: String,
    val errors: List<String>,
) : UserException(
    "Invalid findings report (format `$format`): " + errors.take(MAX_ERRORS).joinToString("; ") +
            (if (errors.size > MAX_ERRORS) "; and ${errors.size - MAX_ERRORS} more error(s)" else "")
) {
    companion object {
        private const val MAX_ERRORS = 20
    }
}

/**
 * A report whose format has no parser.
 */
class FindingsReportUnsupportedFormatException(
    format: String,
    supportedFormats: Collection<String>,
) : UserException(
    "Findings report format `$format` is not supported. Supported formats: ${supportedFormats.sorted().joinToString(", ")}."
)
