package net.nemerosa.ontrack.extension.git.repository

import net.nemerosa.ontrack.extension.git.model.IndexableGitCommit
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.EntityStore
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class GitRepositoryJdbcHelper(dataSource: DataSource) : AbstractJdbcRepository(dataSource), GitRepositoryHelper {

    override fun getEarliestBuildAfterCommit(branch: Branch, indexedGitCommit: IndexableGitCommit): Int? {
        val sql = """
            SELECT e.BUILD
            FROM ENTITY_STORE e
            INNER JOIN BUILDS x ON x.ID = e.BUILD
            WHERE x.BRANCHID = :branchId
            AND e.STORE = :store
            AND e.NAME = :name
            AND (CAST(e.DATA->>'timestamp' AS numeric) >= :timestamp)
            ORDER BY e.BUILD ASC
			LIMIT 1
        """
        return getFirstItem(
                sql,
                params("branchId", branch.id())
                        .addValue("store", IndexableGitCommit.STORE)
                        .addValue("name", EntityStore.DEFAULT_NAME)
                        .addValue("timestamp", indexedGitCommit.timestamp)
                        .addValue("id", indexedGitCommit.commit.id),
                Int::class.java)
    }

    override fun findBranchWithProjectAndGitBranch(project: Project, gitBranch: String): Int? {
        return getFirstItem(
                """SELECT b.ID FROM PROPERTIES p
                        INNER JOIN BRANCHES b ON b.ID = p.BRANCH
                        WHERE p.TYPE = :type
                        AND p.JSON->>'branch' = :branch
                        AND b.PROJECTID = :project
                        """,
                params("type", GitBranchConfigurationPropertyType::class.java.name)
                        .addValue("branch", gitBranch)
                        .addValue("project", project.id()),
                Int::class.java
        )
    }


}