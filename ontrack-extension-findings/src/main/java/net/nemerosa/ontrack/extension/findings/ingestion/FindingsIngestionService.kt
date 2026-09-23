package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.ValidationRun
import tools.jackson.databind.JsonNode

/**
 * Posting the report of a security scan as a validation run of a build.
 */
interface FindingsIngestionService {

    /**
     * Reads a report and, in one transaction, creates the validation run with the counts of the
     * findings, and writes the findings and their observations by this run.
     *
     * A report which cannot be read creates nothing.
     *
     * @param build Build to validate
     * @param request What to post
     * @return The created validation run
     */
    fun ingest(build: Build, request: FindingsIngestionRequest): ValidationRun

    /**
     * Formats which can be ingested
     */
    val formats: Set<String>
}

/**
 * Report of a security scan to post as a validation run.
 *
 * @property validation Name of the validation stamp
 * @property description Description of the validation run
 * @property runInfo Run info of the validation run
 * @property format Format of the report
 * @property kind Kind of scan, taking precedence over the report's
 * @property scanner Name of the scanner, taking precedence over the report's
 * @property report Report
 */
data class FindingsIngestionRequest(
    val validation: String,
    val description: String? = null,
    val runInfo: RunInfoInput? = null,
    val format: String,
    val kind: FindingKind? = null,
    val scanner: String? = null,
    val report: JsonNode,
)
