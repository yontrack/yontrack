package net.nemerosa.ontrack.repository

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class PromotionRunJdbcRepository(
    dataSource: DataSource,
    private val promotionLevelJdbcRepositoryAccessor: PromotionLevelJdbcRepositoryAccessor,
    private val buildJdbcRepositoryAccessor: BuildJdbcRepositoryAccessor,
) : AbstractJdbcRepository(dataSource), PromotionRunRepository, PromotionRunJdbcRepositoryAccessor {

    override fun getPromotionRun(
        id: ID,
        promotionLevel: PromotionLevel?,
        build: Build?
    ): PromotionRun {
        return getFirstItem(
            """
               SELECT *
                FROM promotion_runs
                WHERE id = :id
            """,
            params("id", id.value)
        ) { rs, _ ->
            toPromotionRun(rs, promotionLevel, build)
        } ?: error("Promotion run with ID ${id.value} not found")
    }

    override fun toPromotionRun(rs: ResultSet, promotionLevel: PromotionLevel?, build: Build?): PromotionRun {
        val actualBuild = build ?: buildJdbcRepositoryAccessor.getBuild(id(rs, "buildid"))
        val actualPL = promotionLevel
            ?: promotionLevelJdbcRepositoryAccessor.getPromotionLevel(
                id(rs, "promotionlevelid"),
                actualBuild.branch
            )
        val runId = id(rs)
        return PromotionRun(
            id = runId,
            build = actualBuild,
            promotionLevel = actualPL,
            signature = readSignature(rs),
            description = rs.getString("description"),
            fieldValues = getPromotionRunFieldValues(runId),
        )
    }

    override fun getPromotionRunFieldValues(promotionRunId: ID): List<PromotionRunFieldValue> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT field_name, field_value
                FROM PROMOTION_RUN_FIELD_VALUES
                WHERE PROMOTION_RUN_ID = :promotionRunId
            """.trimIndent(),
            mapOf("promotionRunId" to promotionRunId.value)
        ) { rs, _ ->
            val valueJson: String? = rs.getString("field_value")
            val value: JsonNode? = valueJson?.let { readJson(it) }
            PromotionRunFieldValue(
                name = rs.getString("field_name"),
                value = value,
            )
        }

    override fun savePromotionRunFieldValues(promotionRunId: ID, fieldValues: List<PromotionRunFieldValue>) {
        fieldValues.forEach { fv ->
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO PROMOTION_RUN_FIELD_VALUES (PROMOTION_RUN_ID, FIELD_NAME, FIELD_VALUE)
                    VALUES (:promotionRunId, :fieldName, CAST(:fieldValue AS JSONB))
                """.trimIndent(),
                mapOf(
                    "promotionRunId" to promotionRunId.value,
                    "fieldName" to fv.name,
                    "fieldValue" to fv.value?.let { writeJson(it) },
                )
            )
        }
    }

    override fun getLastPromotionRunForProject(project: Project, promotionName: String): PromotionRun? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT PR.*
                 FROM PROMOTION_RUNS PR
                 INNER JOIN PROMOTION_LEVELS P ON P.ID = PR.PROMOTIONLEVELID
                 INNER JOIN BRANCHES B ON B.ID = P.BRANCHID
                 WHERE B.PROJECTID = :projectId
                 AND P.NAME = :promotionName
                 ORDER BY PR.ID DESC
                 LIMIT 1
            """,
            mapOf(
                "projectId" to project.id(),
                "promotionName" to promotionName,
            )
        ) { rs: ResultSet, _ ->
            toPromotionRun(rs)
        }.firstOrNull()

    override fun getLastPromotionRunForBranch(branch: Branch, promotionName: String) =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT PR.*
                 FROM PROMOTION_RUNS PR
                 INNER JOIN PROMOTION_LEVELS P ON P.ID = PR.PROMOTIONLEVELID
                 WHERE P.BRANCHID = :branchId
                 AND P.NAME = :promotionName
                 ORDER BY PR.ID DESC
                 LIMIT 1
            """,
            mapOf(
                "branchId" to branch.id(),
                "promotionName" to promotionName,
            )
        ) { rs: ResultSet, _ ->
            toPromotionRun(rs)
        }.firstOrNull()

    override fun isBuildPromoted(build: Build, promotionLevel: PromotionLevel): Boolean {
        return namedParameterJdbcTemplate!!.queryForList(
            """
                SELECT ID
                FROM PROMOTION_RUNS
                WHERE BUILDID = :buildId
                AND PROMOTIONLEVELID = :promotionLevelId
            """,
            mapOf(
                "buildId" to build.id(),
                "promotionLevelId" to promotionLevel.id(),
            ),
            Int::class.java
        ).isNotEmpty()
    }


}