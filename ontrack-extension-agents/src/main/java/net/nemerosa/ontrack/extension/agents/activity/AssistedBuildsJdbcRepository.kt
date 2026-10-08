package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangePropertyType
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import javax.sql.DataSource

@Repository
class AssistedBuildsJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), AssistedBuildsRepository {

    override fun countAssistedBuilds(from: LocalDateTime, projects: Collection<Int>): AssistedBuildsCounts {
        if (projects.isEmpty()) {
            return AssistedBuildsCounts.NONE
        }
        // A value stored without its basis was set by the CI, the default of the property
        @Suppress("SqlResolve")
        return namedParameterJdbcTemplate.queryForObject(
            """
                WITH ASSISTED AS (
                    SELECT COALESCE(P.JSON ->> 'basis', :setByCI) AS BASIS,
                           COALESCE(JSONB_ARRAY_LENGTH(CASE WHEN JSONB_TYPEOF(P.JSON -> 'assistants') = 'array' THEN P.JSON -> 'assistants' END), 0) AS ASSISTANTS
                    FROM PROPERTIES P
                    INNER JOIN BUILDS B ON B.ID = P.BUILD
                    INNER JOIN BRANCHES BR ON BR.ID = B.BRANCHID
                    WHERE P.TYPE = :type
                    AND B.CREATION >= :from
                    AND BR.PROJECTID IN (:projects)
                )
                SELECT COUNT(*) FILTER (WHERE BASIS <> :unknown AND ASSISTANTS > 0) AS ASSISTED_COUNT,
                       COUNT(*) FILTER (WHERE BASIS <> :unknown) AS KNOWN_COUNT,
                       COUNT(*) FILTER (WHERE BASIS = :unknown) AS UNKNOWN_COUNT
                FROM ASSISTED
            """.trimIndent(),
            params("type", AssistedChangePropertyType::class.java.name)
                .addValue("from", Time.store(from))
                .addValue("projects", projects)
                .addValue("setByCI", AssistedChangeBasis.SET_BY_CI.name)
                .addValue("unknown", AssistedChangeBasis.UNKNOWN.name),
        ) { rs, _ ->
            AssistedBuildsCounts(
                assisted = rs.getInt("ASSISTED_COUNT"),
                known = rs.getInt("KNOWN_COUNT"),
                unknown = rs.getInt("UNKNOWN_COUNT"),
            )
        } ?: AssistedBuildsCounts.NONE
    }
}
