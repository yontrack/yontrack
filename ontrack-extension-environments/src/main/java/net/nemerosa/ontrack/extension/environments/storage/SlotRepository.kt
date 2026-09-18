package net.nemerosa.ontrack.extension.environments.storage

import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.BuildJdbcRepositoryAccessor
import net.nemerosa.ontrack.repository.ProjectJdbcRepositoryAccessor
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class SlotRepository(
    dataSource: DataSource,
    private val environmentRepositoryAccessor: EnvironmentRepositoryAccessor,
    private val projectJdbcRepositoryAccessor: ProjectJdbcRepositoryAccessor,
    private val buildJdbcRepositoryAccessor: BuildJdbcRepositoryAccessor,
) : AbstractJdbcRepository(dataSource) {

    fun addSlot(slot: Slot) {
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO ENV_SLOTS (ID, ENVIRONMENT_ID, PROJECT_ID, QUALIFIER, DESCRIPTION)
                VALUES (:id, :environmentId, :projectId, :qualifier, :description)
                ON CONFLICT (ID) DO UPDATE SET
                    ENVIRONMENT_ID = EXCLUDED.ENVIRONMENT_ID,
                    PROJECT_ID = EXCLUDED.PROJECT_ID,
                    QUALIFIER = EXCLUDED.QUALIFIER,
                    DESCRIPTION = EXCLUDED.DESCRIPTION;
            """,
            mapOf(
                "id" to slot.id,
                "environmentId" to slot.environment.id,
                "projectId" to slot.project.id(),
                "qualifier" to slot.qualifier,
                "description" to slot.description,
            )
        )
    }

    fun findSlotById(id: String): Slot? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOTS
                WHERE ID = :id
            """,
            mapOf("id" to id),
        ) { rs, _ ->
            toSlot(rs)
        }.firstOrNull()

    fun getSlotById(id: String): Slot =
        findSlotById(id) ?: throw SlotIdNotFoundException(id)

    private fun toSlot(rs: ResultSet) = Slot(
        id = rs.getString("ID"),
        environment = environmentRepositoryAccessor.getEnvironmentById(rs.getString("ENVIRONMENT_ID")),
        project = projectJdbcRepositoryAccessor.getProject(ID.of(rs.getInt("PROJECT_ID"))),
        qualifier = rs.getString("QUALIFIER"),
        description = rs.getString("DESCRIPTION"),
    )

    fun findByEnvironmentAndProjectAndQualifier(environment: Environment, project: Project, qualifier: String): Slot? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOTS
                WHERE ENVIRONMENT_ID = :environmentId
                AND PROJECT_ID = :projectId
                AND QUALIFIER = :qualifier
            """,
            mapOf(
                "environmentId" to environment.id,
                "projectId" to project.id(),
                "qualifier" to qualifier,
            ),
        ) { rs, _ ->
            toSlot(rs)
        }.firstOrNull()

    fun findByEnvironment(environment: Environment): List<Slot> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOTS
                WHERE ENVIRONMENT_ID = :environmentId
            """,
            mapOf(
                "environmentId" to environment.id,
            ),
        ) { rs, _ ->
            toSlot(rs)
        }

    fun findSlotsByProject(project: Project, qualifier: String?): List<Slot> {
        var sql = """
            SELECT *
            FROM ENV_SLOTS
            WHERE PROJECT_ID = :projectId
        """.trimIndent()
        val params: MutableMap<String, Any?> = mapOf("projectId" to project.id()).toMutableMap()
        if (qualifier != null) {
            sql += " AND QUALIFIER = :qualifier"
            params["qualifier"] = qualifier
        }
        return namedParameterJdbcTemplate!!.query(
            sql,
            params
        ) { rs, _ ->
            toSlot(rs)
        }
    }

    /**
     * The slots of these ids, in one query.
     *
     * The matrix asks for a page's worth of slots at once; so does every batch loader behind
     * `Slot.blocked` and friends. Asking for them one by one is the fan-out those exist to remove.
     */
    fun findSlotsByIds(ids: Collection<String>): List<Slot> {
        if (ids.isEmpty()) return emptyList()
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOTS
                WHERE ID IN (:ids)
            """.trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            toSlot(rs)
        }
    }

    /**
     * Every slot of these projects, whatever its qualifier or environment, in one query.
     *
     * "Behind" is the reason this exists: a slot is behind when a slot *upstream of it* holds a
     * newer build, and upstream is read from the whole project-and-qualifier graph - which includes
     * slots the matrix may be hiding behind a tag filter. Computing it from the visible slots alone
     * would make the flag depend on the filter.
     */
    fun findSlotsByProjectIds(projectIds: Collection<Int>): List<Slot> {
        if (projectIds.isEmpty()) return emptyList()
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOTS
                WHERE PROJECT_ID IN (:projectIds)
            """.trimIndent(),
            mapOf("projectIds" to projectIds),
        ) { rs, _ ->
            toSlot(rs)
        }
    }

    fun findSlotByProjectAndEnvironment(environment: Environment, project: Project, qualifier: String): Slot? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM ENV_SLOTS
                WHERE ENVIRONMENT_ID = :environmentId
                AND PROJECT_ID = :projectId
                AND QUALIFIER = :qualifier
            """,
            mapOf(
                "environmentId" to environment.id,
                "projectId" to project.id(),
                "qualifier" to qualifier,
            ),
        ) { rs, _ ->
            toSlot(rs)
        }.firstOrNull()


    fun getEligibleBuilds(
        slot: Slot,
        offset: Int = 0,
        size: Int = 10,
        queries: List<String>,
        params: Map<String, Any?>,
    ): List<Build> {
        var query = """
            SELECT DISTINCT (BD.ID)
            FROM BUILDS BD
                     INNER JOIN BRANCHES B ON BD.BRANCHID = B.ID
                     LEFT JOIN PROMOTION_RUNS PR ON BD.ID = PR.BUILDID
                     LEFT JOIN PROMOTION_LEVELS PL ON B.ID = PL.BRANCHID
            WHERE B.PROJECTID = :projectId
            AND B.DISABLED = FALSE
        """.trimIndent()

        query += queries.joinToString("") { " AND ($it)" }

        val parameters = params.toMutableMap()
        parameters["offset"] = offset

        // Order
        query += """
             ORDER BY BD.ID DESC
            OFFSET :offset
            LIMIT :limit
        """.trimIndent()
        parameters["limit"] = size
        parameters["projectId"] = slot.project.id()

        return namedParameterJdbcTemplate!!.query(
            query,
            parameters,
        ) { rs, _ ->
            val id = rs.getInt("ID")
            buildJdbcRepositoryAccessor.getBuild(ID.of(id))
        }
    }


    fun getCountEligibleBuilds(
        slot: Slot,
        queries: List<String>,
        params: Map<String, Any?>,
    ): Int {
        var query = """
            SELECT COUNT(DISTINCT (BD.ID))
            FROM BUILDS BD
                     INNER JOIN BRANCHES B ON BD.BRANCHID = B.ID
                     LEFT JOIN PROMOTION_RUNS PR ON BD.ID = PR.BUILDID
                     LEFT JOIN PROMOTION_LEVELS PL ON B.ID = PL.BRANCHID
            WHERE B.PROJECTID = :projectId
            AND B.DISABLED = FALSE
        """.trimIndent()

        query += queries.joinToString("") { " AND ($it)" }

        val parameters = params.toMutableMap()
        parameters["projectId"] = slot.project.id()

        return namedParameterJdbcTemplate!!.queryForObject(
            query,
            parameters,
            Int::class.java,
        ) ?: 0
    }

    fun deleteSlot(slot: Slot) {
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENV_SLOTS
                WHERE ID = :id
            """.trimIndent(),
            mapOf(
                "id" to slot.id,
            )
        )
    }

    fun saveSlot(slot: Slot) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE ENV_SLOTS
                SET DESCRIPTION = :description
                WHERE ID = :id
            """.trimIndent(),
            mapOf(
                "id" to slot.id,
                "description" to slot.description,
            )
        )
    }

}