package net.nemerosa.ontrack.kdsl.spec.extension.findings

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectFindingsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidateWithFindingsMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidationRunFindingsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingFilter
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingKind
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingSeverity
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingState
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.ValidationRun
import net.nemerosa.ontrack.kdsl.spec.ValidationStamp
import net.nemerosa.ontrack.kdsl.spec.toValidationRun
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * FQCN of the `security-findings` validation data type. The `security-findings` alias is for
 * `.yontrack/ci.yaml`; the API takes the type itself, as for every other data type.
 */
const val FINDINGS_VALIDATION_DATA_TYPE = "net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType"

/**
 * Format of a findings report.
 *
 * @property id Format as the API expects it
 */
enum class FindingsReportFormat(val id: String) {
    /**
     * Neutral format of Yontrack, not licensed
     */
    FINDINGS("findings"),

    /**
     * SARIF 2.1, needs the licensed feature "Native scanner formats"
     */
    SARIF("sarif"),

    /**
     * Trivy JSON, vulnerabilities only, needs the licensed feature "Native scanner formats"
     */
    TRIVY("trivy"),
}

/**
 * Creates a `security-findings` validation stamp. Its configuration is the one of CHML: a warning
 * and a failure threshold, each a count of findings at a level or above.
 *
 * @param name Name of the validation stamp
 * @param description Description of the validation stamp
 * @param warningLevel Level of the warning threshold (`CRITICAL`, `HIGH`, `MEDIUM` or `LOW`)
 * @param warningValue Count of findings at [warningLevel] or above for a warning
 * @param failedLevel Level of the failure threshold (`CRITICAL`, `HIGH`, `MEDIUM` or `LOW`)
 * @param failedValue Count of findings at [failedLevel] or above for a failure
 * @return Created validation stamp
 */
fun Branch.createFindingsValidationStamp(
    name: String,
    description: String = "",
    warningLevel: String = "HIGH",
    warningValue: Int = 1,
    failedLevel: String = "CRITICAL",
    failedValue: Int = 1,
): ValidationStamp = createValidationStamp(
    name = name,
    description = description,
    dataType = FINDINGS_VALIDATION_DATA_TYPE,
    dataTypeConfig = mapOf(
        "warningLevel" to warningLevel,
        "warningValue" to warningValue,
        "failedLevel" to failedLevel,
        "failedValue" to failedValue,
    ),
)

/**
 * Validates this build with the report of a security scan, creating a validation run with the
 * counts of its findings.
 *
 * @param validation Name of the `security-findings` validation stamp
 * @param format Format of the report
 * @param report Report of the scan
 * @param kind Kind of scan. Required for the native formats, which cannot tell it.
 * @param scanner Name of the scanner, taking precedence over the one given by the report
 * @param description Description of the validation run
 * @param dateTime Time of the scan, the moment of the call when null. The observations, the exposure
 * and the resolution of the findings follow it.
 * @return Created validation run
 */
fun Build.validateWithFindings(
    validation: String,
    format: FindingsReportFormat,
    report: JsonNode,
    kind: FindingKind? = null,
    scanner: String? = null,
    description: String? = null,
    dateTime: LocalDateTime? = null,
): ValidationRun =
    graphqlConnector.mutate(
        ValidateWithFindingsMutation(
            project = branch.project.name,
            branch = branch.name,
            build = name,
            validation = validation,
            description = Optional.presentIfNotNull(description),
            format = format.id,
            kind = Optional.presentIfNotNull(kind),
            scanner = Optional.presentIfNotNull(scanner),
            report = report,
            dateTime = Optional.presentIfNotNull(dateTime),
        )
    ) {
        it?.validateBuildWithFindings?.payloadUserErrors?.convert()
    }
        ?.checkData { it.validateBuildWithFindings?.validationRun }
        ?.validationRunFragment?.toValidationRun(this)
        ?: throw GraphQLMissingDataException("Did not get back the created validation run")

/**
 * Page of the findings of a project.
 *
 * @property totalSize Total number of findings matching the filter
 * @property items Findings of the page
 */
data class FindingPage(
    val totalSize: Int,
    val items: List<Finding>,
)

/**
 * Gets the security findings of this project, the most severe first, then the most recently seen.
 * Empty for a user who is not granted the view of the findings of the project.
 *
 * All the criteria are optional, and apply together.
 *
 * @param severity Maximum severity of the finding across its observations
 * @param state State of the finding: in the project, or on the branch when one is given
 * @param branch Name of a branch the finding has been exposed on, resolved or not
 * @param scanner Name of the scanner which reported the finding
 * @param kind Kind of scan which reported the finding
 * @param offset Offset of the page
 * @param size Size of the page
 */
fun Project.findings(
    severity: FindingSeverity? = null,
    state: FindingState? = null,
    branch: String? = null,
    scanner: String? = null,
    kind: FindingKind? = null,
    offset: Int = 0,
    size: Int = 20,
): FindingPage {
    val page = graphqlConnector.query(
        ProjectFindingsQuery(
            projectId = id.toInt(),
            filter = Optional.present(
                FindingFilter(
                    severity = Optional.presentIfNotNull(severity),
                    state = Optional.presentIfNotNull(state),
                    branch = Optional.presentIfNotNull(branch),
                    scanner = Optional.presentIfNotNull(scanner),
                    kind = Optional.presentIfNotNull(kind),
                )
            ),
            offset = Optional.present(offset),
            size = Optional.present(size),
        )
    )?.project?.findings
    return FindingPage(
        totalSize = page?.pageInfo?.totalSize ?: 0,
        items = page?.pageItems?.map { it.findingFragment.toFinding() } ?: emptyList(),
    )
}

/**
 * Findings reported by this validation run, each with its observation by this run, the most
 * severe first. Empty for a run which is not a security scan, and for a user who is not granted
 * the view of the findings of the project.
 */
val ValidationRun.findings: List<FindingObservation>
    get() = graphqlConnector.query(
        ValidationRunFindingsQuery(id.toInt())
    )?.validationRuns?.firstOrNull()?.findings?.map {
        FindingObservation(
            severity = it.severity,
            rawSeverity = it.rawSeverity,
            installedVersion = it.installedVersion,
            fixedVersion = it.fixedVersion,
            finding = it.finding.findingFragment.toFinding(),
        )
    } ?: emptyList()
