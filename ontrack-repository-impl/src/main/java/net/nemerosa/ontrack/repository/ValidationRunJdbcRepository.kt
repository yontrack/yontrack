package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class ValidationRunJdbcRepository(
    private val dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), ValidationRunRepository {

    override fun updateValidationRunData(
        run: ValidationRun,
        data: ValidationRunData<*>?,
    ): ValidationRun {
        if (data != null) {
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO VALIDATION_RUN_DATA(VALIDATION_RUN, DATA_TYPE_ID, DATA)
                    VALUES (:validationRunId, :dataTypeId, CAST(:data AS JSONB))
                    ON CONFLICT (VALIDATION_RUN) DO UPDATE SET DATA_TYPE_ID = EXCLUDED.DATA_TYPE_ID, DATA = EXCLUDED.DATA
                """.trimIndent(),
                mapOf(
                    "validationRunId" to run.id(),
                    "dataTypeId" to data.descriptor.id,
                    "data" to writeJson(data.data),
                )
            )
        } else {
            namedParameterJdbcTemplate!!.update(
                "DELETE FROM VALIDATION_RUN_DATA WHERE VALIDATION_RUN = :validationRunId",
                mapOf(
                    "validationRunId" to run.id(),
                )
            )
        }
        return run.withData(data)
    }

    override fun getLastValidationRunStatusId(build: Build, validationStamp: ValidationStamp): String? {
        return namedParameterJdbcTemplate!!.queryForList(
            """
                    SELECT VRS.VALIDATIONRUNSTATUSID
                    FROM VALIDATION_RUNS VR
                    INNER JOIN VALIDATION_RUN_STATUSES VRS ON VRS.ID = (SELECT VRST.ID FROM VALIDATION_RUN_STATUSES VRST WHERE VRST.VALIDATIONRUNID = VR.ID ORDER BY VRST.ID DESC LIMIT 1) 
                    WHERE VR.BUILDID = :buildId
                    AND VR.VALIDATIONSTAMPID = :validationStampId
                    ORDER BY VR.ID DESC
                    LIMIT 1
            """,
            mapOf(
                "buildId" to build.id(),
                "validationStampId" to validationStamp.id(),
            ),
            String::class.java
        ).firstOrNull()
    }

    override fun findCascadedValidationRuns(validationStamp: ValidationStamp): List<CascadedValidationRun> {
        val builds = mutableMapOf<Int, Build>()
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT VR.ID, VR.BUILDID, B.NAME, B.DESCRIPTION, B.CREATION, B.CREATOR, B.ACTOR,
                       ROW_NUMBER() OVER (PARTITION BY VR.BUILDID ORDER BY VR.ID) AS RUN_ORDER,
                       (
                           SELECT VRS.VALIDATIONRUNSTATUSID
                           FROM VALIDATION_RUN_STATUSES VRS
                           WHERE VRS.VALIDATIONRUNID = VR.ID
                           ORDER BY VRS.CREATION DESC, VRS.ID DESC
                           LIMIT 1
                       ) AS STATUS
                FROM VALIDATION_RUNS VR
                INNER JOIN BUILDS B ON B.ID = VR.BUILDID
                WHERE VR.VALIDATIONSTAMPID = :validationStampId
                ORDER BY VR.BUILDID, VR.ID
            """,
            mapOf("validationStampId" to validationStamp.id())
        ) { rs, _ ->
            CascadedValidationRun(
                build = builds.getOrPut(rs.getInt("BUILDID")) { toBuild(rs, validationStamp.branch) },
                id = rs.getInt("ID"),
                runOrder = rs.getInt("RUN_ORDER"),
                status = rs.getString("STATUS"),
            )
        }
    }

    /**
     * A build of the [branch] from the columns `BUILDID`, `NAME`, `DESCRIPTION`, `CREATION`,
     * `CREATOR` and `ACTOR`.
     */
    private fun toBuild(rs: ResultSet, branch: Branch) = Build(
        id = ID.of(rs.getInt("BUILDID")),
        name = rs.getString("NAME"),
        description = rs.getString("DESCRIPTION"),
        branch = branch,
        signature = readSignatureWithActor(rs),
    )

}
