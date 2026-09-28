package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class TestSamplesJdbc(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), TestSamples {

    override fun testStamps(branches: Collection<Branch>): List<TestStamp> =
        if (branches.isEmpty()) {
            emptyList()
        } else {
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT ID, BRANCHID, NAME
                    FROM VALIDATION_STAMPS
                    WHERE BRANCHID IN (:branches)
                    AND DATA_TYPE_ID = :dataType
                    ORDER BY BRANCHID, ORDERNB, ID
                """.trimIndent(),
                mapOf(
                    "branches" to branches.map { it.id() },
                    "dataType" to TEST_DATA_TYPE,
                )
            ) { rs, _ ->
                TestStamp(
                    id = rs.getInt("ID"),
                    branchId = rs.getInt("BRANCHID"),
                    name = rs.getString("NAME"),
                )
            }
        }

    override fun runs(stamps: Collection<TestStamp>, interval: Interval): List<TestRunSample> =
        if (stamps.isEmpty()) {
            emptyList()
        } else {
            // The first status of a run is the one it was created with
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT B.BRANCHID, B.ID AS BUILD_ID, VR.VALIDATIONSTAMPID, VR.ID AS RUN_ID,
                           VRS.VALIDATIONRUNSTATUSID, VRS.CREATION
                    FROM VALIDATION_RUNS VR
                    INNER JOIN BUILDS B ON B.ID = VR.BUILDID
                    INNER JOIN VALIDATION_RUN_STATUSES VRS ON VRS.ID = (
                        SELECT MIN(FIRST.ID) FROM VALIDATION_RUN_STATUSES FIRST WHERE FIRST.VALIDATIONRUNID = VR.ID
                    )
                    WHERE VR.VALIDATIONSTAMPID IN (:stamps)
                    AND B.CREATION >= :start AND B.CREATION < :end
                    AND VRS.CREATION < :end
                    ORDER BY B.CREATION, B.ID, VR.ID
                """.trimIndent(),
                mapOf(
                    "stamps" to stamps.map { it.id },
                    "start" to Time.store(interval.start),
                    "end" to Time.store(interval.end),
                )
            ) { rs, _ ->
                TestRunSample(
                    branchId = rs.getInt("BRANCHID"),
                    buildId = rs.getInt("BUILD_ID"),
                    stampId = rs.getInt("VALIDATIONSTAMPID"),
                    runId = rs.getInt("RUN_ID"),
                    status = rs.getString("VALIDATIONRUNSTATUSID"),
                    time = Time.fromStorage(rs.getString("CREATION"))!!,
                )
            }
        }

    companion object {
        /**
         * Data type of the test stamps, as stored on the validation stamps
         */
        private val TEST_DATA_TYPE: String = TestSummaryValidationDataType::class.qualifiedName!!
    }
}
