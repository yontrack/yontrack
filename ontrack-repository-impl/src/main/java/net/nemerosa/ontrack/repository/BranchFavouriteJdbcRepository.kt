package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

@Repository
class BranchFavouriteJdbcRepository(
        dataSource: DataSource
) : AbstractJdbcRepository(dataSource), BranchFavouriteRepository {

    override fun getFavouriteBranches(accountId: Int): List<Int> {
        return namedParameterJdbcTemplate!!.queryForList(
                """
                    SELECT B.ID FROM BRANCHES B
                    INNER JOIN BRANCH_FAVOURITES BF ON BF.BRANCHID = B.ID
                    WHERE BF.ACCOUNTID = :accountId
                """,
                params("accountId", accountId),
                Int::class.java
        ).filterNotNull()
    }

    override fun isBranchFavourite(accountId: Int, branchId: Int): Boolean {
        return getFirstItem(
                "SELECT ID FROM BRANCH_FAVOURITES WHERE ACCOUNTID = :account AND BRANCHID = :branch",
                params("account", accountId).addValue("branch", branchId),
                Int::class.java
        ) != null
    }

    override fun setBranchFavourite(accountId: Int, branchId: Int, favourite: Boolean) {
        if (favourite) {
            if (!isBranchFavourite(accountId, branchId)) {
                namedParameterJdbcTemplate!!.update(
                        "INSERT INTO BRANCH_FAVOURITES(ACCOUNTID, BRANCHID) VALUES (:account, :branch)",
                        params("account", accountId).addValue("branch", branchId)
                )
            }
        } else {
            namedParameterJdbcTemplate!!.update(
                    "DELETE FROM BRANCH_FAVOURITES WHERE ACCOUNTID = :account AND BRANCHID = :branch",
                    params("account", accountId).addValue("branch", branchId)
            )
        }
    }
}
