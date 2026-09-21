package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.Ack.Companion.one
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.getNullableInt
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

@Repository
class BuildFilterJdbcRepository(
    dataSource: DataSource
) : AbstractJdbcRepository(dataSource), BuildFilterRepository {

    override fun findForBranch(branchId: Int): Collection<TBuildFilter> {
        return order(
            namedParameterJdbcTemplate!!.query(
                "(SELECT * FROM BUILD_FILTERS WHERE BRANCHID = :branchId)" +
                        " UNION " +
                        "(SELECT NULL AS accountId, * FROM SHARED_BUILD_FILTERS WHERE BRANCHID = :branchId)",
                params("branchId", branchId)
            ) { rs: ResultSet, row: Int -> toBuildFilter(rs) })
    }

    override fun findForBranch(accountId: Int?, branchId: Int): Collection<TBuildFilter> {
        return if (accountId != null) {
            order(
                namedParameterJdbcTemplate!!.query(
                    "(SELECT * FROM BUILD_FILTERS WHERE ACCOUNTID = :accountId AND BRANCHID = :branchId)" +
                            " UNION " +
                            "(SELECT NULL AS accountId, * FROM SHARED_BUILD_FILTERS WHERE BRANCHID = :branchId)",
                    params("branchId", branchId).addValue("accountId", accountId)
                ) { rs, _ -> toBuildFilter(rs) })
        } else {
            namedParameterJdbcTemplate!!.query(
                "SELECT NULL AS accountId, * FROM SHARED_BUILD_FILTERS WHERE BRANCHID = :branchId ORDER BY NAME",
                params("branchId", branchId)
            ) { rs, _ -> toBuildFilter(rs) }
        }
    }

    protected fun order(filters: List<TBuildFilter>): List<TBuildFilter> {
        // Shared filters first
        // Order by name then
        return filters.sortedWith { o1, o2 ->
            val a1: Int? = o1.accountId
            val a2: Int? = o2.accountId
            if (a1 != null && a2 != null) {
                o1.name.compareTo(o2.name)
            } else if (a1 != null) {
                1
            } else {
                -1
            }
        }
    }

    override fun findByBranchAndName(accountId: Int, branchId: Int, name: String): TBuildFilter? {
        val params = params("branchId", branchId).addValue("accountId", accountId).addValue("name", name)
        // Looks first for shared filters
        return getFirstItem(
            "SELECT NULL AS accountId, * FROM SHARED_BUILD_FILTERS WHERE BRANCHID = :branchId AND NAME = :name",
            params
        ) { rs, _ -> toBuildFilter(rs) }
            ?: getFirstItem(
                "SELECT * FROM BUILD_FILTERS WHERE ACCOUNTID = :accountId AND BRANCHID = :branchId AND NAME = :name",
                params
            ) { rs, _ -> toBuildFilter(rs) }
    }

    override fun save(accountId: Int?, branchId: Int, name: String, type: String, data: JsonNode): Ack {
        if (accountId != null) {
            val params = params("branchId", branchId).addValue("accountId", accountId).addValue("name", name)
            namedParameterJdbcTemplate!!.update(
                "DELETE FROM BUILD_FILTERS WHERE ACCOUNTID = :accountId AND BRANCHID = :branchId AND NAME = :name",
                params
            )
            return one(
                namedParameterJdbcTemplate!!.update(
                    "INSERT INTO BUILD_FILTERS (ACCOUNTID, BRANCHID, NAME, TYPE, DATA) " +
                            "VALUES (:accountId, :branchId, :name, :type, CAST(:data AS JSONB))",
                    params.addValue("type", type).addValue("data", writeJson(data))
                )
            )
        } else {
            val params = params("branchId", branchId).addValue("name", name)
            namedParameterJdbcTemplate!!.update(
                "DELETE FROM SHARED_BUILD_FILTERS WHERE BRANCHID = :branchId AND NAME = :name",
                params
            )
            return one(
                namedParameterJdbcTemplate!!.update(
                    "INSERT INTO SHARED_BUILD_FILTERS (BRANCHID, NAME, TYPE, DATA) " +
                            "VALUES (:branchId, :name, :type, CAST(:data AS JSONB))",
                    params.addValue("type", type).addValue("data", writeJson(data))
                )
            )
        }
    }

    override fun delete(accountId: Int?, branchId: Int, name: String, shared: Boolean): Ack {
        // Account filter
        val accountFilterDeleted = if (accountId != null) {
            one(
                namedParameterJdbcTemplate!!.update(
                    "DELETE FROM BUILD_FILTERS WHERE ACCOUNTID = :accountId AND BRANCHID = :branchId AND NAME = :name",
                    params("branchId", branchId).addValue("accountId", accountId).addValue("name", name)
                )
            )
        } else {
            Ack.NOK
        }
        // Shared filter
        return if (shared) {
            accountFilterDeleted.or(
                one(
                    namedParameterJdbcTemplate!!.update(
                        "DELETE FROM SHARED_BUILD_FILTERS WHERE BRANCHID = :branchId AND NAME = :name",
                        params("branchId", branchId).addValue("name", name)
                    )
                )
            )
        } else {
            accountFilterDeleted
        }
    }

    @Throws(SQLException::class)
    private fun toBuildFilter(rs: ResultSet): TBuildFilter {
        return TBuildFilter(
            rs.getNullableInt("accountId"),
            rs.getInt("branchId"),
            rs.getString("name"),
            rs.getString("type"),
            readJson(rs, "data")
        )
    }
}
