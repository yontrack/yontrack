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

    companion object {
        private const val INSERT = """
            INSERT INTO SEARCH_DOCUMENTS (TYPE, KEY, PROJECT_ID, ENTITY_TYPE, ENTITY_ID, TITLE, IDENTIFIERS, TEXT, DATA, UPDATED_AT, INDEXED_AT)
            VALUES (:type, :key, :projectId, :entityType, :entityId, :title, :identifiers, :text, CAST(:data AS JSONB), :updatedAt, :indexedAt)
        """
    }

    override fun save(documents: List<SearchDocument>, indexedAt: LocalDateTime) {
        if (documents.isEmpty()) return
        namedParameterJdbcTemplate.batchUpdate(
            """
                $INSERT
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
            params(documents, indexedAt)
        )
    }

    override fun insertIfAbsent(documents: List<SearchDocument>, indexedAt: LocalDateTime): Int {
        if (documents.isEmpty()) return 0
        return namedParameterJdbcTemplate.batchUpdate(
            """
                $INSERT
                ON CONFLICT (TYPE, KEY) DO NOTHING
            """,
            params(documents, indexedAt)
        ).sumOf { count -> count.coerceAtLeast(0) }
    }

    private fun params(documents: List<SearchDocument>, indexedAt: LocalDateTime) =
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

    override fun delete(type: String, key: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SEARCH_DOCUMENTS WHERE TYPE = :type AND KEY = :key",
            params("type", type).addValue("key", key)
        )
    }

    override fun deleteForEntity(entity: ProjectEntityID): Int {
        val params = params("entityType", entity.type.name).addValue("entityId", entity.id)
        return when (entity.type) {
            ProjectEntityType.BRANCH -> namedParameterJdbcTemplate.update(
                """
                    DELETE FROM SEARCH_DOCUMENTS
                    WHERE (ENTITY_TYPE = :entityType AND ENTITY_ID = :entityId)
                    OR (
                        ENTITY_TYPE = :buildType
                        AND ENTITY_ID IN (SELECT B.ID FROM BUILDS B WHERE B.BRANCHID = :entityId)
                    )
                """,
                params.addValue("buildType", ProjectEntityType.BUILD.name)
            )

            else -> namedParameterJdbcTemplate.update(
                "DELETE FROM SEARCH_DOCUMENTS WHERE ENTITY_TYPE = :entityType AND ENTITY_ID = :entityId",
                params
            )
        }
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
        highlight: Boolean,
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
                sql.highlighted(sql.perType, highlight),
                MapSqlParameterSource(sql.params.values).addValue("perType", perType)
            ) { rs, _ -> toHit(rs, highlight) }
        } else if (size <= 0) {
            // Facets only
            emptyList()
        } else {
            namedParameterJdbcTemplate.query(
                sql.highlighted(sql.page, highlight),
                MapSqlParameterSource(sql.params.values).addValue("offset", offset).addValue("size", size)
            ) { rs, _ -> toHit(rs, highlight) }
        }
        return SearchDocumentPage(
            total = facets.values.sum(),
            facets = facets,
            items = items,
        )
    }

    private fun toHit(rs: ResultSet, highlight: Boolean) = SearchDocumentHit(
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
        highlight = if (highlight) {
            rs.getString("HIGHLIGHT")?.let { SearchHeadline.parse(it) }?.takeIf { it.isNotEmpty() }
        } else {
            null
        },
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
            .addValue("anyTsq", parsed.anyWordTsQuery)
            .addValue("headlineFrom", SearchHeadline.TRANSLATE_FROM)
            .addValue("headlineTo", SearchHeadline.TRANSLATE_TO)
            .addValue("headlineOptions", SearchHeadline.OPTIONS)
            .addValue("types", scope.types)
            .addValue("projectIds", scope.projectIds)
            .addValue("projectLessTypes", scope.projectLessTypes)
            .addValue("nonFuzzyTypes", scope.nonFuzzyTypes)

        private val tierConditions: List<Pair<SearchMatchTier, String>> = parsed.tiers.map { tier ->
            tier to when (tier) {
                SearchMatchTier.EXACT -> "d.IDENTIFIERS LIKE :exact"
                SearchMatchTier.PREFIX -> "(lower(d.TITLE) LIKE :titlePrefix OR d.IDENTIFIERS LIKE :identifierPrefix)"
                SearchMatchTier.FULL_TEXT -> "d.TSV @@ to_tsquery('simple', :tsq)"
                SearchMatchTier.TRIGRAM -> if (scope.nonFuzzyTypes.isEmpty()) {
                    "(:q <% d.TITLE OR :q <% d.IDENTIFIERS)"
                } else {
                    "(d.TYPE NOT IN (:nonFuzzyTypes) AND (:q <% d.TITLE OR :q <% d.IDENTIFIERS))"
                }
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
            // Types visible in some of the visible projects only
            val restrictions = scope.restrictedTypes.entries.withIndex().joinToString("") { (index, entry) ->
                val (type, projectIds) = entry
                params.addValue("restrictedType$index", type)
                if (projectIds.isEmpty()) {
                    " AND (d.TYPE <> :restrictedType$index OR d.PROJECT_ID IS NULL)"
                } else {
                    params.addValue("restrictedProjectIds$index", projectIds)
                    " AND (d.TYPE <> :restrictedType$index OR d.PROJECT_ID IS NULL OR d.PROJECT_ID IN (:restrictedProjectIds$index))"
                }
            }
            "($projects OR $projectLess)$restrictions"
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

        /**
         * Rows of a page, best first
         */
        val page = """
            SELECT c.*, $score AS SCORE
            FROM candidates c
            ORDER BY $ranking
            OFFSET :offset LIMIT :size
        """.trimIndent()

        /**
         * Best rows of each type
         */
        val perType = """
            SELECT c.*, $score AS SCORE
            FROM (
                SELECT c.*, ROW_NUMBER() OVER (PARTITION BY c.TYPE ORDER BY $ranking) AS RN
                FROM candidates c
            ) c
            WHERE c.RN <= :perType
            ORDER BY $ranking
        """.trimIndent()

        /**
         * Complete query for some [rows][page] of the candidates, with their `HIGHLIGHT` when asked
         * for.
         *
         * `ts_headline` is expensive: it runs in an outer query, on the rows already selected -
         * never on the other candidates - and only on the free text matching one of the words of
         * the query.
         */
        fun highlighted(rows: String, highlight: Boolean): String =
            if (highlight) {
                """
                    $candidates
                    SELECT c.*,
                           CASE WHEN c.TEXT IS NOT NULL AND to_tsvector('simple', c.TEXT) @@ to_tsquery('simple', :anyTsq)
                                THEN ts_headline('simple', translate(c.TEXT, :headlineFrom, :headlineTo), to_tsquery('simple', :anyTsq), :headlineOptions)
                           END AS HIGHLIGHT
                    FROM ($rows) c
                    ORDER BY $ranking
                """.trimIndent()
            } else {
                """
                    $candidates
                    $rows
                """.trimIndent()
            }
    }

}
