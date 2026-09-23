package net.nemerosa.ontrack.extension.findings.model

/**
 * Severity of a finding, as asserted by a scanner on an observation.
 *
 * `UNKNOWN` is what a scanner gives when it has no severity to give.
 */
enum class FindingSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}
