package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class SecuritySamplesJdbc(
    dataSource: DataSource,
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

    private fun findingKind(name: String): FindingKind? = FindingKind.entries.find { it.name == name }

    companion object {
        /**
         * Data type of the `security-findings` runs
         */
        private val FINDINGS_DATA_TYPE: String = FindingsValidationDataType::class.qualifiedName!!
    }
}
