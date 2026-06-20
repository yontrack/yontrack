package net.nemerosa.ontrack.repository

import com.fasterxml.jackson.databind.node.ArrayNode
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionLevelField
import net.nemerosa.ontrack.model.structure.PromotionLevelFieldType
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class PromotionLevelJdbcRepository(
    dataSource: DataSource,
    private val branchJdbcRepositoryAccessor: BranchJdbcRepositoryAccessor,
) : AbstractJdbcRepository(dataSource), PromotionLevelRepository, PromotionLevelJdbcRepositoryAccessor {

    override fun getPromotionLevel(id: ID, branch: Branch?): PromotionLevel =
        getFirstItem(
            """
               SELECT *
                FROM promotion_levels
                WHERE id = :id
            """,
            params("id", id.value)
        ) { rs, _ ->
            toPromotionLevel(rs, branch)
        } ?: error("Promotion level with ID ${id.value} not found")

    override fun findNamesByToken(token: String?): List<String> =
        if (token.isNullOrBlank()) {
            jdbcTemplate!!.queryForList(
                """SELECT DISTINCT(NAME) FROM PROMOTION_LEVELS ORDER BY NAME""",
                String::class.java
            )
        } else {
            namedParameterJdbcTemplate!!.queryForList(
                """SELECT DISTINCT(NAME) FROM PROMOTION_LEVELS WHERE NAME ILIKE :name ORDER BY NAME""",
                mapOf("name" to "%$token%"),
                String::class.java
            )
        }

    override fun findPromotionLevelNamesByProject(project: Project, token: String?): List<String> =
        if (token.isNullOrBlank()) {
            namedParameterJdbcTemplate!!.queryForList(
                """
                    SELECT DISTINCT(PL.NAME)
                    FROM PROMOTION_LEVELS PL
                    INNER JOIN BRANCHES B ON PL.BRANCHID = B.ID
                    WHERE B.PROJECTID = :projectId
                    ORDER BY PL.NAME
                """.trimIndent(),
                mapOf(
                    "projectId" to project.id()
                ),
                String::class.java
            )
        } else {
            namedParameterJdbcTemplate!!.queryForList(
                """
                    SELECT DISTINCT(PL.NAME)
                    FROM PROMOTION_LEVELS PL
                    INNER JOIN BRANCHES B ON PL.BRANCHID = B.ID
                    WHERE B.PROJECTID = :projectId
                    AND PL.NAME ILIKE :token
                    ORDER BY PL.NAME
                """.trimIndent(),
                mapOf(
                    "projectId" to project.id(),
                    "token" to "%${token}%",
                ),
                String::class.java
            )
        }

    override fun findByToken(token: String?): List<PromotionLevel> =
        if (token.isNullOrBlank()) {
            jdbcTemplate!!.query(
                """
                    SELECT *
                    FROM promotion_levels
                """
            ) { rs, _ ->
                toPromotionLevel(rs)
            }
        } else {
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT *
                    FROM promotion_levels
                    WHERE name ILIKE :name
                """,
                mapOf("name" to "%$token%")
            ) { rs, _ ->
                toPromotionLevel(rs)
            }
        }

    override fun findBranchesWithPromotionLevel(
        project: Project,
        promotionLevelName: String,
    ): List<Branch> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT B.ID
                FROM BRANCHES B
                INNER JOIN PROMOTION_LEVELS PL ON PL.BRANCHID = B.ID
                LEFT JOIN (
                	SELECT DISTINCT ON (BRANCHID) BRANCHID, CREATION
                	FROM BUILDS
                	ORDER BY BRANCHID, CREATION DESC
                ) LAST_BUILD ON LAST_BUILD.BRANCHID = B.ID
                WHERE B.PROJECTID = :projectId
                AND PL.NAME = :promotionLevelName
                AND B.DISABLED = FALSE
                ORDER BY COALESCE(LAST_BUILD.CREATION, B.CREATION) DESC
            """.trimIndent(),
            mapOf(
                "projectId" to project.id(),
                "promotionLevelName" to promotionLevelName,
            )
        ) { rs, _ ->
            branchJdbcRepositoryAccessor.getBranch(id(rs), project)
        }

    override fun toPromotionLevel(rs: ResultSet, branch: Branch?): PromotionLevel {
        val plId = id(rs)
        return PromotionLevel(
            id = plId,
            name = rs.getString("name"),
            description = rs.getString("description"),
            branch = branch ?: branchJdbcRepositoryAccessor.getBranch(id(rs, "branchid")),
            isImage = !rs.getString("imagetype").isNullOrBlank(),
            signature = readSignature(rs),
            fields = getPromotionLevelFields(plId),
        )
    }

    override fun getPromotionLevelFields(promotionLevelId: ID): List<PromotionLevelField> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM PROMOTION_LEVEL_FIELDS
                WHERE PROMOTION_LEVEL_ID = :promotionLevelId
                ORDER BY POSITION
            """.trimIndent(),
            mapOf("promotionLevelId" to promotionLevelId.value)
        ) { rs, _ ->
            val optionsJson = rs.getString("options")
            val options = if (optionsJson != null) {
                val node = readJson(optionsJson)
                (node as? ArrayNode)?.map { it.asText() } ?: emptyList()
            } else {
                emptyList()
            }
            PromotionLevelField(
                id = rs.getInt("id"),
                name = rs.getString("name"),
                displayName = rs.getString("display_name"),
                description = rs.getString("description"),
                type = PromotionLevelFieldType.valueOf(rs.getString("field_type")),
                required = rs.getBoolean("required"),
                options = options,
                position = rs.getInt("position"),
            )
        }

    override fun setPromotionLevelFields(promotionLevelId: ID, fields: List<PromotionLevelField>) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM PROMOTION_LEVEL_FIELDS WHERE PROMOTION_LEVEL_ID = :promotionLevelId",
            mapOf("promotionLevelId" to promotionLevelId.value)
        )
        fields.forEachIndexed { index, field ->
            val optionsJson = if (field.type == PromotionLevelFieldType.CHOICE && field.options.isNotEmpty()) {
                writeJson(field.options)
            } else {
                null
            }
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO PROMOTION_LEVEL_FIELDS
                        (PROMOTION_LEVEL_ID, NAME, DISPLAY_NAME, DESCRIPTION, FIELD_TYPE, REQUIRED, OPTIONS, POSITION)
                    VALUES
                        (:promotionLevelId, :name, :displayName, :description, :fieldType, :required, CAST(:options AS JSONB), :position)
                """.trimIndent(),
                mapOf(
                    "promotionLevelId" to promotionLevelId.value,
                    "name" to field.name,
                    "displayName" to field.displayName,
                    "description" to field.description,
                    "fieldType" to field.type.name,
                    "required" to field.required,
                    "options" to optionsJson,
                    "position" to index,
                )
            )
        }
    }
}