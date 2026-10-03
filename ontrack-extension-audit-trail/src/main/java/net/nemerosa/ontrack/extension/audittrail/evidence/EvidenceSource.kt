package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName

/**
 * Where an evidence comes from, as its client claims it.
 *
 * @property tool Tool which produced the evidence, like `trivy`
 * @property version Version of the tool
 * @property url Where the evidence was produced — a CI job, a report: an HTTP or HTTPS URL
 */
@APIName("EvidenceSource")
@APIDescription("Where an evidence comes from, as its client claims it")
data class EvidenceSource(
    @APIDescription("Tool which produced the evidence, like trivy")
    val tool: String?,
    @APIDescription("Version of the tool")
    val version: String?,
    @APIDescription("Where the evidence was produced - a CI job, a report: an HTTP or HTTPS URL")
    val url: String?,
)
