package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class SecuritySamplesJdbc(
    dataSource: DataSource,
    private val findingRepository: FindingRepository,
) : AbstractJdbcRepository(dataSource), SecuritySamples {

    override fun securityStamps(branches: Collection<Branch>): List<SecurityStamp> =
        if (branches.isEmpty()) {
            emptyList()
        } else {
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT VS.ID, VS.BRANCHID, VS.NAME
                    FROM VALIDATION_STAMPS VS
                    WHERE VS.BRANCHID IN (:branches)
                    AND (
                        VS.DATA_TYPE_ID = :dataType
                        OR EXISTS (
                            SELECT 1
                            FROM VALIDATION_RUNS VR
                            INNER JOIN VALIDATION_RUN_DATA VRD ON VRD.VALIDATION_RUN = VR.ID
                            WHERE VR.VALIDATIONSTAMPID = VS.ID
                            AND VRD.DATA_TYPE_ID = :dataType
                        )
                    )
                    ORDER BY VS.BRANCHID, VS.ORDERNB, VS.ID
                """.trimIndent(),
                mapOf(
                    "branches" to branches.map { it.id() },
                    "dataType" to FINDINGS_DATA_TYPE,
                )
            ) { rs, _ ->
                SecurityStamp(
                    id = rs.getInt("ID"),
                    branchId = rs.getInt("BRANCHID"),
                    name = rs.getString("NAME"),
                )
            }
        }

    override fun scans(branches: Collection<Branch>, interval: Interval): List<SecurityRunSample> =
        if (branches.isEmpty()) {
            emptyList()
        } else {
            // The first status of a run is the one it was created with, from the thresholds of its stamp.
            // The kind of a scan is in its data; a scan posted before it was recorded there gets the
            // kinds of the findings it observed.
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT B.BRANCHID, VR.VALIDATIONSTAMPID, VR.ID AS RUN_ID,
                           VRS.VALIDATIONRUNSTATUSID, VRS.CREATION,
                           VRD.DATA ->> 'kind' AS KIND,
                           ARRAY(
                               SELECT DISTINCT F.KIND
                               FROM FINDING_OBSERVATIONS FO
                               INNER JOIN FINDINGS F ON F.ID = FO.FINDING_ID
                               WHERE FO.VALIDATION_RUN_ID = VR.ID
                           ) AS OBSERVED_KINDS
                    FROM VALIDATION_RUNS VR
                    INNER JOIN BUILDS B ON B.ID = VR.BUILDID
                    INNER JOIN VALIDATION_RUN_DATA VRD ON VRD.VALIDATION_RUN = VR.ID
                    INNER JOIN VALIDATION_RUN_STATUSES VRS ON VRS.ID = (
                        SELECT MIN(FIRST.ID) FROM VALIDATION_RUN_STATUSES FIRST WHERE FIRST.VALIDATIONRUNID = VR.ID
                    )
                    WHERE B.BRANCHID IN (:branches)
                    AND VRD.DATA_TYPE_ID = :dataType
                    AND VRS.CREATION >= :start AND VRS.CREATION < :end
                    ORDER BY VRS.CREATION, VR.ID
                """.trimIndent(),
                mapOf(
                    "branches" to branches.map { it.id() },
                    "dataType" to FINDINGS_DATA_TYPE,
                    "start" to Time.store(interval.start),
                    "end" to Time.store(interval.end),
                )
            ) { rs, _ ->
                val kind = rs.getString("KIND")?.let(::findingKind)
                val observed = (rs.getArray("OBSERVED_KINDS")?.array as? Array<*>)
                    ?.mapNotNull { (it as? String)?.let(::findingKind) }
                    ?.toSet()
                    ?: emptySet()
                SecurityRunSample(
                    branchId = rs.getInt("BRANCHID"),
                    stampId = rs.getInt("VALIDATIONSTAMPID"),
                    runId = rs.getInt("RUN_ID"),
                    status = rs.getString("VALIDATIONRUNSTATUSID"),
                    time = Time.fromStorage(rs.getString("CREATION"))!!,
                    kinds = kind?.let { setOf(it) } ?: observed,
                )
            }
        }

    override fun remediationFindings(
        project: Project,
        branches: Collection<Branch>,
        interval: Interval,
    ): List<SecurityFindingSample> {
        val findings = namedParameterJdbcTemplate!!.query(
            """
                SELECT F.ID, F.MAX_SEVERITY, F.FIRST_SEEN, F.RESOLVED_AT
                FROM FINDINGS F
                WHERE F.PROJECT_ID = :project
                AND F.MAX_SEVERITY IN (:severities)
                AND (F.RESOLVED_AT IS NULL OR F.RESOLVED_AT >= :start)
                ORDER BY F.ID
            """.trimIndent(),
            mapOf(
                "project" to project.id(),
                "severities" to REMEDIATION_SEVERITIES.map { it.name },
                "start" to Time.store(interval.start),
            )
        ) { rs, _ ->
            SecurityFindingSample(
                findingId = rs.getInt("ID"),
                severity = FindingSeverity.valueOf(rs.getString("MAX_SEVERITY")),
                firstSeen = Time.fromStorage(rs.getString("FIRST_SEEN"))!!,
                resolvedAt = Time.fromStorage(rs.getString("RESOLVED_AT")),
                state = FindingState.RESOLVED,
            )
        }
        // State of the findings not resolved, from their exposure on the branches in scope, the
        // acceptances evaluated on the last day of the interval
        val date = interval.end.toLocalDate()
        val branchIds = branches.map { it.id() }.toSet()
        val exposures = findingRepository.findExposuresByFindings(
            findings.filter { it.resolvedAt == null }.map { it.findingId }
        )
            .filter { it.branchId in branchIds }
            .groupBy { it.findingId }
        return findings.map { finding ->
            if (finding.resolvedAt != null) {
                finding
            } else {
                finding.copy(
                    state = FindingState.of(
                        FindingExposureState.of(
                            exposures[finding.findingId]?.map { it.stateOn(date) } ?: emptyList()
                        )
                    )
                )
            }
        }
    }

    private fun findingKind(name: String): FindingKind? = FindingKind.entries.find { it.name == name }

    companion object {
        /**
         * Data type of the `security-findings` runs
         */
        private val FINDINGS_DATA_TYPE: String = FindingsValidationDataType::class.qualifiedName!!

        /**
         * Maximum severities of the findings read by the remediation readings
         */
        private val REMEDIATION_SEVERITIES = listOf(FindingSeverity.CRITICAL, FindingSeverity.HIGH)
    }
}
