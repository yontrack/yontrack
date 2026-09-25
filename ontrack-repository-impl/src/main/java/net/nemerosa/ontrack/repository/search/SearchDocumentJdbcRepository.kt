package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * The one place where the SQL of the search lives. Indexers only describe documents.
 */
@Repository
class SearchDocumentJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), SearchDocumentRepository {

    override fun save(documents: List<SearchDocument>, indexedAt: LocalDateTime) {
        if (documents.isEmpty()) return
        namedParameterJdbcTemplate.batchUpdate(
            """
                INSERT INTO SEARCH_DOCUMENTS (TYPE, KEY, PROJECT_ID, ENTITY_TYPE, ENTITY_ID, TITLE, IDENTIFIERS, TEXT, DATA, UPDATED_AT, INDEXED_AT)
                VALUES (:type, :key, :projectId, :entityType, :entityId, :title, :identifiers, :text, CAST(:data AS JSONB), :updatedAt, :indexedAt)
                ON CONFLICT (TYPE, KEY) DO UPDATE SET
                    PROJECT_ID = EXCLUDED.PROJECT_ID,
                    ENTITY_TYPE = EXCLUDED.ENTITY_TYPE,
                    ENTITY_ID = EXCLUDED.ENTITY_ID,
                    TITLE = EXCLUDED.TITLE,
                    IDENTIFIERS = EXCLUDED.IDENTIFIERS,
                    TEXT = EXCLUDED.TEXT,
                    DATA = EXCLUDED.DATA,
                    UPDATED_AT = EXCLUDED.UPDATED_AT,
                    INDEXED_AT = EXCLUDED.INDEXED_AT
            """,
            documents.map { document ->
                MapSqlParameterSource()
                    .addValue("type", document.type)
                    .addValue("key", document.key)
                    .addValue("projectId", document.projectId, Types.INTEGER)
                    .addValue("entityType", document.entity?.type?.name, Types.VARCHAR)
                    .addValue("entityId", document.entity?.id, Types.INTEGER)
                    .addValue("title", document.title)
                    .addValue("identifiers", SearchDocumentIdentifiers.encode(document.identifiers))
                    .addValue("text", document.text, Types.VARCHAR)
                    .addValue("data", writeJson(document.data))
                    .addValue("updatedAt", document.updatedAt ?: indexedAt)
                    .addValue("indexedAt", indexedAt)
            }.toTypedArray()
        )
    }

    override fun delete(type: String, key: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SEARCH_DOCUMENTS WHERE TYPE = :type AND KEY = :key",
            params("type", type).addValue("key", key)
        )
    }

    override fun deleteIndexedBefore(type: String, time: LocalDateTime): Int =
        namedParameterJdbcTemplate.update(
            "DELETE FROM SEARCH_DOCUMENTS WHERE TYPE = :type AND INDEXED_AT < :time",
            params("type", type).addValue("time", time)
        )

    override fun deleteAll(type: String): Int =
        namedParameterJdbcTemplate.update(
            "DELETE FROM SEARCH_DOCUMENTS WHERE TYPE = :type",
            params("type", type)
        )

    override fun search(
        query: String,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
    ): SearchDocumentPage? {
        val parsed = ParsedSearchQuery.parse(query) ?: return null
        if (scope.types.isEmpty()) {
            return SearchDocumentPage(total = 0, facets = emptyMap(), items = emptyList())
        }
        val sql = SearchDocumentQuery(parsed, scope)
        // Facets, and the total out of them
        val facets = mutableMapOf<String, Int>()
        namedParameterJdbcTemplate.query(sql.facets, sql.params) { rs ->
            facets[rs.getString("TYPE")] = rs.getInt("N")
        }
        // Page
        val items = if (facets.isEmpty()) {
            emptyList()
        } else if (perType != null) {
            namedParameterJdbcTemplate.query(
                sql.perType,
                MapSqlParameterSource(sql.params.values).addValue("perType", perType)
            ) { rs, _ -> toHit(rs) }
        } else {
            namedParameterJdbcTemplate.query(
                sql.page,
                MapSqlParameterSource(sql.params.values).addValue("offset", offset).addValue("size", size)
            ) { rs, _ -> toHit(rs) }
        }
        return SearchDocumentPage(
            total = facets.values.sum(),
            facets = facets,
            items = items,
        )
    }

    private fun toHit(rs: ResultSet) = SearchDocumentHit(
        type = rs.getString("TYPE"),
        key = rs.getString("KEY"),
        projectId = rs.getInt("PROJECT_ID").takeIf { !rs.wasNull() },
        entity = rs.getString("ENTITY_TYPE")?.let { entityType ->
            ProjectEntityID(
                type = ProjectEntityType.valueOf(entityType),
                id = rs.getInt("ENTITY_ID"),
            )
        },
        title = rs.getString("TITLE"),
        text = rs.getString("TEXT"),
        data = readJson(rs, "DATA"),
        updatedAt = rs.getObject("UPDATED_AT", LocalDateTime::class.java),
        score = rs.getDouble("SCORE"),
    )

    /**
     * SQL of a search.
     *
     * The candidates are the documents of the scope matching at least one of the tiers of the
     * query. Each one gets its tier (the strongest it matches) and a relevance within its tier (full-
     * text rank, trigram similarity). The ranking is: tier, relevance within the tier, recency,
     * then the display order of the type. There is no precedence of a type over another before
     * that: an exact build name beats a vague project match.
     */
    private class SearchDocumentQuery(
        parsed: ParsedSearchQuery,
        scope: SearchDocumentScope,
    ) {

        val params = MapSqlParameterSource()
            .addValue("q", parsed.text)
            .addValue("exact", parsed.exactPattern)
            .addValue("titlePrefix", parsed.titlePrefixPattern)
            .addValue("identifierPrefix", parsed.identifierPrefixPattern)
            .addValue("tsq", parsed.tsQuery)
            .addValue("types", scope.types)
            .addValue("projectIds", scope.projectIds)
            .addValue("projectLessTypes", scope.projectLessTypes)

        private val tierConditions: List<Pair<SearchMatchTier, String>> = parsed.tiers.map { tier ->
            tier to when (tier) {
                SearchMatchTier.EXACT -> "d.IDENTIFIERS LIKE :exact"
                SearchMatchTier.PREFIX -> "(lower(d.TITLE) LIKE :titlePrefix OR d.IDENTIFIERS LIKE :identifierPrefix)"
                SearchMatchTier.FULL_TEXT -> "d.TSV @@ to_tsquery('simple', :tsq)"
                SearchMatchTier.TRIGRAM -> "(:q <% d.TITLE OR :q <% d.IDENTIFIERS)"
            }
        }

        private val tier = tierConditions.joinToString(
            prefix = "CASE ",
            separator = " ",
            postfix = " END"
        ) { (tier, condition) ->
            "WHEN $condition THEN ${tier.ordinal + 1}"
        }

        private val relevance = tierConditions.joinToString(
            prefix = "CASE ",
            separator = " ",
            postfix = " ELSE 0 END"
        ) { (tier, condition) ->
            "WHEN $condition THEN " + when (tier) {
                SearchMatchTier.EXACT, SearchMatchTier.PREFIX -> "0"
                SearchMatchTier.FULL_TEXT -> "least(ts_rank(d.TSV, to_tsquery('simple', :tsq)), 0.999)"
                SearchMatchTier.TRIGRAM -> "least(greatest(word_similarity(:q, d.TITLE), word_similarity(:q, d.IDENTIFIERS)), 0.999)"
            }
        }

        private val access: String = run {
            val projectLess = if (scope.projectLessTypes.isEmpty()) {
                "FALSE"
            } else {
                "(d.PROJECT_ID IS NULL AND d.TYPE IN (:projectLessTypes))"
            }
            val projects = when {
                scope.allProjects -> "d.PROJECT_ID IS NOT NULL"
                scope.projectIds.isEmpty() -> "FALSE"
                else -> "d.PROJECT_ID IN (:projectIds)"
            }
            "($projects OR $projectLess)"
        }

        private val typeOrder = scope.types.withIndex().joinToString(
            prefix = "CASE c.TYPE ",
            separator = " ",
            postfix = " END"
        ) { (index, type) ->
            params.addValue("type$index", type)
            "WHEN :type$index THEN $index"
        }

        private val candidates = """
            WITH candidates AS (
                SELECT d.ID, d.TYPE, d.KEY, d.PROJECT_ID, d.ENTITY_TYPE, d.ENTITY_ID, d.TITLE, d.TEXT, d.DATA, d.UPDATED_AT,
                       $tier AS TIER,
                       $relevance AS RELEVANCE
                FROM SEARCH_DOCUMENTS d
                WHERE d.TYPE IN (:types)
                AND $access
                AND (${tierConditions.joinToString(" OR ") { it.second }})
            )
        """.trimIndent()

        private val ranking = "c.TIER, c.RELEVANCE DESC, c.UPDATED_AT DESC, $typeOrder, c.ID"

        /**
         * Score, the higher the better, carrying the tier: 4 for exact, 3 for prefix, [2, 3[ for
         * full-text, [1, 2[ for trigram.
         */
        private val score = "(${SearchMatchTier.entries.size + 1} - c.TIER + c.RELEVANCE)"

        val facets = """
            $candidates
            SELECT c.TYPE, COUNT(*) AS N FROM candidates c GROUP BY c.TYPE
        """.trimIndent()

        val page = """
            $candidates
            SELECT c.*, $score AS SCORE
            FROM candidates c
            ORDER BY $ranking
            OFFSET :offset LIMIT :size
        """.trimIndent()

        val perType = """
            $candidates
            SELECT c.*, $score AS SCORE
            FROM (
                SELECT c.*, ROW_NUMBER() OVER (PARTITION BY c.TYPE ORDER BY $ranking) AS RN
                FROM candidates c
            ) c
            WHERE c.RN <= :perType
            ORDER BY $ranking
        """.trimIndent()
    }

}
