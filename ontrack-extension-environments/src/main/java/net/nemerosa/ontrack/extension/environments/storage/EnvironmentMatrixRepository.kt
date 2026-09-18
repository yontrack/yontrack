package net.nemerosa.ontrack.extension.environments.storage

import net.nemerosa.ontrack.extension.environments.EnvironmentMatrixFilter
import net.nemerosa.ontrack.extension.environments.onActivity
import net.nemerosa.ontrack.extension.environments.onEnvironments
import net.nemerosa.ontrack.extension.environments.onProjects
import net.nemerosa.ontrack.extension.environments.onFavourites
import net.nemerosa.ontrack.extension.environments.onTags
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import javax.sql.DataSource

/**
 * The matrix's own reading of the slot tables: **which projects**, and then **which slots**.
 *
 * It is a repository of its own rather than two more methods on [SlotRepository] because it answers
 * a different question. [SlotRepository] answers "the slots of this environment", "the slots of this
 * project" - one row of the matrix at a time, which is exactly the shape the matrix must not use.
 * Here the filtering and the paging happen in SQL, once, over the whole table, and the service above
 * assembles what comes back.
 *
 * The two-step - project ids first, their slots second - is what makes the paging honest. Paging a
 * join of projects and slots pages *slots*, so a page of twenty rows would hold four projects with
 * five environments each; the matrix pages projects, so the project ids are selected and limited on
 * their own and the slots of that page are then fetched in one go.
 */
@Repository
class EnvironmentMatrixRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource) {

    /**
     * The ids of the projects having at least one slot matching [filter], by project name.
     *
     * @param accountId The current account, needed by the favourites filter. A null account has no
     *   favourite, so `favourites = true` then matches nothing - which is the truth rather than an
     *   error.
     */
    fun findProjectIds(
        filter: EnvironmentMatrixFilter,
        accountId: Int?,
        offset: Int,
        size: Int,
    ): List<Int> {
        val (where, params) = criteria(filter, accountId)
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT DISTINCT P.ID, P.NAME
                FROM ENV_SLOTS S
                INNER JOIN PROJECTS P ON P.ID = S.PROJECT_ID
                INNER JOIN ENVIRONMENTS E ON E.ID = S.ENVIRONMENT_ID
                WHERE $where
                ORDER BY P.NAME
                LIMIT :size OFFSET :offset
            """.trimIndent(),
            params + mapOf("size" to size, "offset" to offset),
        ) { rs, _ -> rs.getInt("ID") }
    }

    /**
     * How many projects match [filter] altogether.
     */
    fun countProjects(
        filter: EnvironmentMatrixFilter,
        accountId: Int?,
    ): Int {
        val (where, params) = criteria(filter, accountId)
        return namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(DISTINCT P.ID)
                FROM ENV_SLOTS S
                INNER JOIN PROJECTS P ON P.ID = S.PROJECT_ID
                INNER JOIN ENVIRONMENTS E ON E.ID = S.ENVIRONMENT_ID
                WHERE $where
            """.trimIndent(),
            params,
            Int::class.java,
        ) ?: 0
    }

    /**
     * Does this account have any favourite project at all?
     *
     * Asked without looking at the environments on purpose: the toolbar's question is "has this
     * person ever starred a project", and answering it with "has this person starred a project
     * which has a slot" would silently move somebody who has favourites onto All.
     */
    fun hasFavourites(accountId: Int?): Boolean {
        if (accountId == null) return false
        return (namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(*)
                FROM PROJECT_FAVOURITES
                WHERE ACCOUNTID = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            Int::class.java,
        ) ?: 0) > 0
    }

    /**
     * The ids of the slots of these projects, restricted to the column filters.
     *
     * The *column* filters are here - tags and environment names - and the row filters are not,
     * because the rows have already been chosen by [findProjectIds]. No ordering: the service groups
     * these slots into rows and columns and sorts each by what the screen reads them by, so an order
     * imposed here would only be one more thing that has to agree with it.
     */
    fun findSlotIdsByProjects(
        projectIds: Collection<Int>,
        tags: List<String>,
        environmentNames: List<String> = emptyList(),
        activity: Boolean = false,
    ): List<String> {
        if (projectIds.isEmpty()) return emptyList()
        val params = mutableMapOf<String, Any?>("projectIds" to projectIds)
        var where = "S.PROJECT_ID IN (:projectIds)"
        if (tags.isNotEmpty()) {
            where += " AND E.TAGS && CAST(:tags AS TEXT[])"
            params["tags"] = tags.toPgArray()
        }
        if (environmentNames.isNotEmpty()) {
            where += " AND E.NAME IN (:environmentNames)"
            params["environmentNames"] = environmentNames
        }
        if (activity) {
            // "Only with activity" hides *rows*, and a row is a project and a qualifier across every
            // *visible* environment - so a slot is kept when anything is moving anywhere on its row,
            // not only on itself. Otherwise turning the filter on would leave a row showing the one
            // cell that happens to be busy and blank out the environments it came from.
            //
            // "Visible" matters: the row is looked at through the same column filters as the matrix
            // itself. Without that, a row could be kept because something is moving in an
            // environment this matrix is not showing, and it would be drawn with every cell quiet -
            // which is exactly what the filter was turned on to hide.
            var rowCriteria = """
                RS.PROJECT_ID = S.PROJECT_ID
                  AND RS.QUALIFIER = S.QUALIFIER
                  AND RP.STATUS IN ($ACTIVE_STATUSES)
            """.trimIndent()
            if (tags.isNotEmpty()) {
                rowCriteria += " AND RE.TAGS && CAST(:tags AS TEXT[])"
            }
            if (environmentNames.isNotEmpty()) {
                rowCriteria += " AND RE.NAME IN (:environmentNames)"
            }
            where += """
                AND EXISTS (
                    SELECT 1
                    FROM ENV_SLOTS RS
                    INNER JOIN ENVIRONMENTS RE ON RE.ID = RS.ENVIRONMENT_ID
                    INNER JOIN ENV_SLOT_PIPELINE RP ON RP.SLOT_ID = RS.ID
                    WHERE $rowCriteria
                )
            """.trimIndent()
        }
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT S.ID
                FROM ENV_SLOTS S
                INNER JOIN ENVIRONMENTS E ON E.ID = S.ENVIRONMENT_ID
                WHERE $where
            """.trimIndent(),
            params,
        ) { rs, _ -> rs.getString("ID") }
    }

    /**
     * The `WHERE` of both the page and the count, so the two can never drift apart.
     */
    private fun criteria(filter: EnvironmentMatrixFilter, accountId: Int?): Pair<String, Map<String, Any?>> {
        val criteria = mutableListOf("1 = 1")
        val params = mutableMapOf<String, Any?>()

        if (!filter.project.isNullOrBlank()) {
            // Escaped, and the escape character declared: a search box takes whatever somebody
            // types, and `_` is a single-character wildcard in LIKE - so a search for `a_b` would
            // otherwise also match `axb`, which is not what typing a project name means.
            criteria += "P.NAME ILIKE :projectName ESCAPE '\\'"
            params["projectName"] = "%${filter.project.trim().escapeLike()}%"
        }

        if (filter.onFavourites) {
            if (accountId == null) {
                // Nobody's favourites are everybody's favourites would be the wrong answer.
                criteria += "1 = 0"
            } else {
                criteria += """
                    EXISTS (
                        SELECT 1 FROM PROJECT_FAVOURITES F
                        WHERE F.PROJECTID = P.ID AND F.ACCOUNTID = :accountId
                    )
                """.trimIndent()
                params["accountId"] = accountId
            }
        }

        filter.label?.let { label ->
            criteria += """
                EXISTS (
                    SELECT 1 FROM PROJECT_LABEL PL
                    WHERE PL.PROJECT_ID = P.ID AND PL.LABEL_ID = :labelId
                )
            """.trimIndent()
            params["labelId"] = label
        }

        if (filter.onProjects.isNotEmpty()) {
            criteria += "P.NAME IN (:projectNames)"
            params["projectNames"] = filter.onProjects
        }

        if (filter.onEnvironments.isNotEmpty()) {
            criteria += "E.NAME IN (:environmentNames)"
            params["environmentNames"] = filter.onEnvironments
        }

        if (filter.onTags.isNotEmpty()) {
            criteria += "E.TAGS && CAST(:tags AS TEXT[])"
            params["tags"] = filter.onTags.toPgArray()
        }

        if (filter.onActivity) {
            criteria += """
                EXISTS (
                    SELECT 1 FROM ENV_SLOT_PIPELINE PIP
                    WHERE PIP.SLOT_ID = S.ID AND PIP.STATUS IN ($ACTIVE_STATUSES)
                )
            """.trimIndent()
        }

        return criteria.joinToString(" AND ") to params
    }

    companion object {
        /**
         * The statuses a deployment which is still on its way can be in, as a SQL list.
         */
        private val ACTIVE_STATUSES: String = SlotPipelineStatus.activeStatuses
            .joinToString(", ") { "'${it.name}'" }

        /**
         * The LIKE metacharacters, escaped with a backslash - which the query declares as its
         * `ESCAPE` character rather than relying on the server's default.
         */
        private fun String.escapeLike(): String =
            replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        /**
         * A `TEXT[]` literal, which is how a list of tags is compared with the `&&` (overlaps)
         * operator. The driver has no mapping for a Kotlin list onto a Postgres array in a named
         * parameter, so the literal is built here and cast in the query.
         */
        private fun List<String>.toPgArray(): String =
            joinToString(",", prefix = "{", postfix = "}") { tag ->
                "\"" + tag.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            }
    }

}
