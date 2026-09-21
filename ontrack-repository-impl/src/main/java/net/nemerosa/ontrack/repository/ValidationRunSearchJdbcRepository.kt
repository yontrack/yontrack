package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class ValidationRunSearchJdbcRepository(
        dataSource: DataSource,
        private val structureRepository: StructureRepository
) : AbstractJdbcRepository(dataSource), ValidationRunSearchRepository {

    override fun searchProjectValidationRuns(
            project: Project,
            request: ValidationRunSearchRequest,
            validationRunStatusService: (String) -> ValidationRunStatusID
    ): List<ValidationRun> {
        val params = MapSqlParameterSource()

        val query = prepareProjectValidationRuns(project, request, params)
        @Suppress("SqlResolve")
        val sql = """
            SELECT r.ID
            $query
            ORDER BY r.id DESC
            LIMIT :size
            OFFSET :offset
        """
        params.addValue("size", request.size).addValue("offset", request.offset)

        return namedParameterJdbcTemplate!!.queryForList(
                sql,
                params,
                Int::class.java
        ).filterNotNull().map { id ->
            structureRepository.getValidationRun(ID.of(id)) {
                validationRunStatusService(it)
            }
        }
    }

    override fun totalProjectValidationRuns(project: Project, request: ValidationRunSearchRequest): Int {
        val params = MapSqlParameterSource()

        val query = prepareProjectValidationRuns(project, request, params)
        @Suppress("SqlResolve")
        val sql = """
            SELECT COUNT(r.ID)
            $query
        """

        return namedParameterJdbcTemplate!!.queryForObject(
                sql,
                params,
                Int::class.java
        ) ?: 0
    }

    private fun prepareProjectValidationRuns(
            project: Project,
            request: ValidationRunSearchRequest,
            params: MapSqlParameterSource
    ): String {
        val criterias = StringBuilder()
        params.addValue("project", project.id())

        if (!request.branch.isNullOrBlank()) {
            criterias.append(" AND b.NAME ~ :branch")
            params.addValue("branch", request.branch)
        }

        if (!request.validationStamp.isNullOrBlank()) {
            criterias.append(" AND t.NAME ~ :validationStamp")
            params.addValue("validationStamp", request.validationStamp)
        }

        if (!request.statuses.isNullOrBlank()) {
            criterias.append(" AND s.VALIDATIONRUNSTATUSID ~ :statuses")
            params.addValue("statuses", request.statuses)
        }

        return """
            FROM VALIDATION_RUNS r
            INNER JOIN VALIDATION_RUN_STATUSES s ON s.validationrunid = r.id
            INNER JOIN VALIDATION_STAMPS t ON t.id = r.validationstampid
            INNER JOIN BRANCHES b ON b.id = t.branchid
            WHERE b.projectid = :project
            $criterias
        """
    }
}