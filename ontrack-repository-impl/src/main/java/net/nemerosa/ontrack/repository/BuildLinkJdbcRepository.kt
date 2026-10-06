package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class BuildLinkJdbcRepository(
    dataSource: DataSource,
    private val buildJdbcRepositoryAccessor: BuildJdbcRepositoryAccessor,
) : AbstractJdbcRepository(dataSource), BuildLinkRepository {

    override fun deleteBuildLink(fromBuild: Build, toBuild: Build, qualifier: String): Boolean =
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM BUILD_LINKS 
                WHERE BUILDID = :fromBuildId 
                AND TARGETBUILDID = :toBuildId
                AND QUALIFIER = :qualifier
            """,
            mapOf(
                "fromBuildId" to fromBuild.id(),
                "toBuildId" to toBuild.id(),
                "qualifier" to qualifier,
            )
        ) > 0

    override fun createBuildLink(fromBuild: Build, toBuild: Build, qualifier: String): Boolean {
        val existing = deleteBuildLink(fromBuild, toBuild, qualifier)
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO BUILD_LINKS(BUILDID, TARGETBUILDID, QUALIFIER) 
                VALUES (:fromBuildId, :toBuildId, :qualifier)
                ON CONFLICT(BUILDID, TARGETBUILDID, QUALIFIER) DO NOTHING
            """,
            mapOf(
                "fromBuildId" to fromBuild.id(),
                "toBuildId" to toBuild.id(),
                "qualifier" to qualifier,
            )
        )
        return !existing
    }

    override fun getCountQualifiedBuildsUsedBy(build: Build): Int =
        namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(BL.ID)
                FROM BUILD_LINKS BL
                WHERE BL.BUILDID = :buildId
            """,
            mapOf("buildId" to build.id()),
            Int::class.java
        ) ?: 0

    override fun getQualifiedBuildsUsedBy(build: Build): List<BuildLink> {
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT B.*, BL.QUALIFIER AS QUALIFIER
                FROM BUILDS B
                INNER JOIN BUILD_LINKS BL ON B.ID = BL.TARGETBUILDID
                WHERE BL.BUILDID = :buildId 
                ORDER BY B.ID DESC
            """,
            mapOf("buildId" to build.id())
        ) { rs, _ ->
            BuildLink(
                build = buildJdbcRepositoryAccessor.getBuild(id(rs)),
                qualifier = rs.getString("QUALIFIER"),
            )
        }
    }

    override fun getQualifiedBuildsUsing(build: Build): List<BuildLink> {
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT F.*, BL.QUALIFIER 
                FROM BUILDS F 
                INNER JOIN BUILD_LINKS BL ON BL.BUILDID = F.ID 
                WHERE BL.TARGETBUILDID = :buildId 
                ORDER BY F.ID DESC
                """,
            params("buildId", build.id())
        ) { rs, _ ->
            BuildLink(
                build = buildJdbcRepositoryAccessor.getBuild(id(rs)),
                qualifier = rs.getString("QUALIFIER")
            )
        }
    }

    override fun isLinkedTo(build: Build, project: String, buildPattern: String?, qualifier: String?): Boolean =
        getFirstItem(
            """
                SELECT BL.TARGETBUILDID 
                FROM BUILD_LINKS BL 
                INNER JOIN BUILDS T ON BL.TARGETBUILDID = T.ID 
                INNER JOIN BRANCHES BR ON BR.ID = T.BRANCHID 
                INNER JOIN PROJECTS P ON P.ID = BR.PROJECTID 
                WHERE BL.BUILDID = :buildId 
                AND T.NAME LIKE :buildNamePattern
                AND BL.QUALIFIER LIKE :qualifierPattern
                AND P.NAME = :projectName 
                LIMIT 1
                """,
            params("buildId", build.id())
                .addValue("buildNamePattern", expandBuildPattern(buildPattern))
                .addValue("projectName", project)
                .addValue("qualifierPattern", qualifier ?: "%"),
            Int::class.java
        ) != null

    override fun isLinkedTo(build: Build, targetBuild: Build, qualifier: String?): Boolean =
        if (qualifier == null) {
            getFirstItem(
                """
                    SELECT BL.TARGETBUILDID
                    FROM BUILD_LINKS BL
                    WHERE BL.BUILDID = :buildId
                    AND BL.TARGETBUILDID = :targetBuildId
                """,
                mapOf(
                    "buildId" to build.id(),
                    "targetBuildId" to targetBuild.id(),
                ),
                Int::class.java
            ) != null
        } else {
            getFirstItem(
                """
                    SELECT BL.TARGETBUILDID
                    FROM BUILD_LINKS BL
                    WHERE BL.BUILDID = :buildId
                    AND BL.TARGETBUILDID = :targetBuildId
                    AND BL.QUALIFIER = :qualifier
                """,
                mapOf(
                    "buildId" to build.id(),
                    "targetBuildId" to targetBuild.id(),
                    "qualifier" to qualifier,
                ),
                Int::class.java
            ) != null
        }

    override fun isLinkedFrom(build: Build, project: String, buildPattern: String?, qualifier: String?): Boolean =
        getFirstItem(
            """
                SELECT BL.BUILDID 
                FROM BUILD_LINKS BL 
                INNER JOIN BUILDS F ON BL.BUILDID = F.ID 
                INNER JOIN BRANCHES BR ON BR.ID = F.BRANCHID 
                INNER JOIN PROJECTS P ON P.ID = BR.PROJECTID 
                WHERE BL.TARGETBUILDID = :buildId 
                AND F.NAME LIKE :buildNamePattern 
                AND BL.QUALIFIER LIKE :qualifierPattern
                AND P.NAME = :projectName 
                LIMIT 1""",
            params("buildId", build.id())
                .addValue("buildNamePattern", expandBuildPattern(buildPattern))
                .addValue("projectName", project)
                .addValue("qualifierPattern", qualifier ?: "%"),
            Int::class.java
        ) != null

    override fun forEachBuildLink(code: (from: Build, to: Build, qualifier: String) -> Unit) {
        jdbcTemplate!!.query(
            "SELECT * FROM BUILD_LINKS ORDER BY ID ASC"
        ) { rs ->
            processBuildLink(rs, code)
        }
    }

    override fun forEachBuildLinkTo(project: Project, code: (from: Build, to: Build, qualifier: String) -> Unit) {
        namedParameterJdbcTemplate!!.query(
            """
                SELECT l.*
                FROM BUILD_LINKS l
                INNER JOIN BUILDS t ON t.ID = l.TARGETBUILDID
                INNER JOIN BRANCHES b ON b.ID = t.BRANCHID
                WHERE b.PROJECTID = :projectId
                ORDER BY l.ID ASC
            """,
            params("projectId", project.id())
        ) { rs ->
            processBuildLink(rs, code)
        }
    }

    private fun processBuildLink(rs: ResultSet, code: (from: Build, to: Build, qualifier: String) -> Unit) {
        val buildId = id(rs, "buildId")
        val targetBuildId = id(rs, "targetBuildId")
        val qualifier = rs.getString("qualifier")
        // Loads the build
        val build: Build = buildJdbcRepositoryAccessor.getBuild(buildId)
        val targetBuild: Build = buildJdbcRepositoryAccessor.getBuild(targetBuildId)
        // Processing
        code(build, targetBuild, qualifier)
    }
}