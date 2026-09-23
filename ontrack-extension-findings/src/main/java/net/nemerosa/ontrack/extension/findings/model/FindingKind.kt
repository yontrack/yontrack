package net.nemerosa.ontrack.extension.findings.model

/**
 * What kind of scan reported a finding.
 */
enum class FindingKind {
    IMAGE,
    CODE,
    SECRETS,
    DAST,
    DEPENDENCIES,
    OTHER,
}
