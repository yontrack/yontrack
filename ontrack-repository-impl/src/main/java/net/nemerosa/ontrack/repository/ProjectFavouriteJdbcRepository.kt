package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class ProjectFavouriteJdbcRepository(dataSource: DataSource) : AbstractJdbcRepository(dataSource), ProjectFavouriteRepository {

    override fun getFavouriteProjects(accountId: Int): List<Int> {
        return namedParameterJdbcTemplate!!.queryForList(
                """
                    SELECT P.ID FROM PROJECTS P 
                    INNER JOIN PROJECT_FAVOURITES PF ON PF.PROJECTID = P.ID
                    WHERE PF.ACCOUNTID = :accountId
                """,
                params("accountId", accountId),
                Int::class.java
        ).filterNotNull()
    }

    override fun isProjectFavourite(accountId: Int, projectId: Int): Boolean {
        return getFirstItem(
                "SELECT ID FROM PROJECT_FAVOURITES WHERE ACCOUNTID = :account AND PROJECTID = :project",
                params("account", accountId).addValue("project", projectId),
                Int::class.java
        ) != null
    }

    override fun setProjectFavourite(accountId: Int, projectId: Int, favourite: Boolean) {
        if (favourite) {
            if (!isProjectFavourite(accountId, projectId)) {
                namedParameterJdbcTemplate!!.update(
                        "INSERT INTO PROJECT_FAVOURITES(ACCOUNTID, PROJECTID) VALUES (:account, :project)",
                        params("account", accountId).addValue("project", projectId)
                )
            }
        } else {
            namedParameterJdbcTemplate!!.update(
                    "DELETE FROM PROJECT_FAVOURITES WHERE ACCOUNTID = :account AND PROJECTID = :project",
                    params("account", accountId).addValue("project", projectId)
            )
        }
    }
}