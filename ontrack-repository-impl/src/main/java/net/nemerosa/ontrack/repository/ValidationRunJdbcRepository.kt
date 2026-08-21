package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
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
                "INSERT INTO VALIDATION_RUN_DATA(VALIDATION_RUN, DATA_TYPE_ID, DATA) VALUES (:validationRunId, :dataTypeId, CAST(:data AS JSONB))",
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

}