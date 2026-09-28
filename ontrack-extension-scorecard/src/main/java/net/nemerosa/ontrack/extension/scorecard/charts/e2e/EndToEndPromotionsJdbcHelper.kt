package net.nemerosa.ontrack.extension.scorecard.charts.e2e

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class EndToEndPromotionsJdbcHelper(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EndToEndPromotionsHelper {

    companion object {
        private const val QUERY_MAX_FETCH = 100
        private const val QUERY = """
            with recursive links as (
	
                select 1 as depth, p.name as ref_project, b.name as ref_branch, n.name as ref_build, n.creation as ref_build_creation, pl.id as ref_promotion_id, pl.name as ref_promotion, pr.creation as ref_promotion_creation, n.id as build_id, p.name as project, b.name as branch, n.name as build, n.creation as build_creation, pl.id as promotion_id, pl.name as promotion, pr.creation as promotion_creation, array[n.id] as visited
                from builds n
                inner join branches b on b.id = n.branchid
                inner join projects p on p.id = b.projectid
                inner join promotion_levels pl on pl.branchid = b.id
                left join promotion_runs pr on pr.buildid = n.id and pr.promotionlevelid = pl.id
                
                union
                
                select links.depth + 1 as depth, links.ref_project, links.ref_branch, links.ref_build, links.ref_build_creation, links.ref_promotion_id, links.ref_promotion, links.ref_promotion_creation, n.id as build_id, p.name as project, b.name as branch, n.name as build, n.creation as build_creation, pl.id as promotion_id, pl.name as promotion, pr.creation as promotion_creation, visited || n.id
                from build_links l
                inner join links on links.build_id = l.targetbuildid
                inner join builds n on l.buildid = n.id
                inner join branches b on b.id = n.branchid
                inner join projects p on p.id = b.projectid
                inner join promotion_levels pl on pl.branchid = b.id
                left join promotion_runs pr on pr.buildid = n.id and pr.promotionlevelid = pl.id
                where not (n.id = any (visited))
                
            )
            select *
            from links
            where depth >= :minDepth
            and depth <= :maxDepth
        """
        private const val QUERY_NO_DEPTH = """
            select *
            from (
                select 1 as depth, p.name as ref_project, b.name as ref_branch, n.name as ref_build, n.creation as ref_build_creation, pl.id as ref_promotion_id, pl.name as ref_promotion, pr.creation as ref_promotion_creation, n.id as build_id, p.name as project, b.name as branch, n.name as build, n.creation as build_creation, pl.id as promotion_id, pl.name as promotion, pr.creation as promotion_creation
                from builds n
                inner join branches b on b.id = n.branchid
                inner join projects p on p.id = b.projectid
                inner join promotion_levels pl on pl.branchid = b.id
                left join promotion_runs pr on pr.buildid = n.id and pr.promotionlevelid = pl.id
            ) as links
            where 1 = 1
        """
    }

    /**
     * Make sure to use a cursor to navigate the result set.
     *
     * See https://jdbc.postgresql.org/documentation/head/query.html#query-with-cursor
     */
    override fun createJdbcTemplate(dataSource: DataSource): JdbcTemplate {
        val template = super.createJdbcTemplate(dataSource)
        template.fetchSize = QUERY_MAX_FETCH
        return template
    }

    override fun forEachEndToEndPromotionRecord(
        filter: EndToEndPromotionFilter,
        code: (record: EndToEndPromotionRecord) -> Unit,
    ) {
        val params = MapSqlParameterSource()
        var query: String
        // Optimization: not using recursivity when maxDepth == 1
        if (filter.maxDepth == 1) {
            query = QUERY_NO_DEPTH
        } else {
            query = QUERY
            // Parameters
            params.addValue("minDepth", filter.minDepth).addValue("maxDepth", filter.maxDepth)
        }

        // Query filters
        if (filter.samePromotion) {
            query += " and ref_promotion = promotion"
        }
        if (filter.promotionId != null) {
            query += " and ref_promotion_id =  :refPromotionId"
            params.addValue("refPromotionId", filter.promotionId)
        }
        if (filter.targetPromotionId != null) {
            query += " and promotion_id = :targetPromotionId"
            params.addValue("targetPromotionId", filter.targetPromotionId)
        }
        if (filter.targetProject != null) {
            query += " and project = :targetProject"
            params.addValue("targetProject", filter.targetProject)
        }
        if (filter.afterTime != null) {
            query += " and ref_build_creation >= :afterTime"
            params.addValue("afterTime", Time.store(filter.afterTime))
        }
        if (filter.beforeTime != null) {
            query += " and ref_build_creation <= :beforeTime"
            params.addValue("beforeTime", Time.store(filter.beforeTime))
        }

        // Ordering
        query += " order by build_id asc"

        // Runs the template
        namedParameterJdbcTemplate!!.query(query, params) { rs ->
            code(rs.toRecord())
        }
    }

    private fun ResultSet.toRecord() = EndToEndPromotionRecord(
        depth = getInt("depth"),
        ref = EndToEndPromotionNode(
            project = getString("ref_project"),
            branch = getString("ref_branch"),
            build = getString("ref_build"),
            buildCreation = dateTimeFromDB(getString("ref_build_creation"))!!,
            promotionId = getInt("ref_promotion_id"),
            promotion = getString("ref_promotion"),
            promotionCreation = dateTimeFromDB(getString("ref_promotion_creation"))
        ),
        target = EndToEndPromotionNode(
            project = getString("project"),
            branch = getString("branch"),
            build = getString("build"),
            buildCreation = dateTimeFromDB(getString("build_creation"))!!,
            promotionId = getInt("promotion_id"),
            promotion = getString("promotion"),
            promotionCreation = dateTimeFromDB(getString("promotion_creation"))
        )
    )
}

