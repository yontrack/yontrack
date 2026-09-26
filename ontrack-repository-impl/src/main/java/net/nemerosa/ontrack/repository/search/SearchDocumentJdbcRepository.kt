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
        countCap: Int,
    ): SearchDocumentPage? {
        val parsed = ParsedSearchQuery.parse(query) ?: return null
        if (scope.types.isEmpty()) {
            return SearchDocumentPage(total = 0, facets = emptyMap(), items = emptyList())
        }
        val statements = statements(parsed, scope, offset, size, perType, highlight, countCap)
        val facets = mutableMapOf<String, SearchDocumentCount>()
        val facet = { rs: ResultSet ->
            facets[rs.getString("TYPE")] = SearchDocumentCount(count = rs.getInt("N"), capped = rs.getBoolean("CAPPED"))
        }
        val items = if (statements.facets == null) {
            // Best rows of each type, each one carrying the count of its type: every type with a
            // candidate has at least one row, so the facets come with them, in one scan
            statements.rows?.let { rows ->
                namedParameterJdbcTemplate.query(rows.sql, rows.params) { rs, _ ->
                    facet(rs)
                    toHit(rs, highlight)
                }
            } ?: emptyList()
        } else {
            // Facets, and the total out of them
            namedParameterJdbcTemplate.query(statements.facets.sql, statements.facets.params) { rs ->
                facet(rs)
            }
            // Rows, unless there is no candidate at all
            if (facets.isEmpty() || statements.rows == null) {
                emptyList()
            } else {
                namedParameterJdbcTemplate.query(statements.rows.sql, statements.rows.params) { rs, _ ->
                    toHit(rs, highlight)
                }
            }
        }
        return SearchDocumentPage(
            total = facets.values.sumOf { it.count },
            facets = facets,
            items = items,
        )
    }

    override fun setLocalWorkMem(workMem: String) {
        // SET does not take a parameter, set_config does: `true` for the transaction only
        namedParameterJdbcTemplate.queryForObject(
            "SELECT set_config('work_mem', :workMem, true)",
            params("workMem", workMem),
            String::class.java,
        )
    }

    /**
     * The statements [search] runs for the same arguments, for the `searchPerfTest`, which
     * `EXPLAIN`s them. The SQL of the search lives in one place only.
     *
     * @return `null` if the query is too short to be searched, or if there is no type to search into
     */
    fun searchStatements(
        query: String,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
        highlight: Boolean,
        countCap: Int = SearchDocumentRepository.DEFAULT_COUNT_CAP,
    ): SearchDocumentStatements? {
        val parsed = ParsedSearchQuery.parse(query) ?: return null
        if (scope.types.isEmpty()) return null
        return statements(parsed, scope, offset, size, perType, highlight, countCap)
    }

    private fun statements(
        parsed: ParsedSearchQuery,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
        highlight: Boolean,
        countCap: Int,
    ): SearchDocumentStatements {
        val sql = SearchDocumentQuery(parsed, scope, countCap.coerceAtLeast(1))
        val rows = if (perType != null) {
            SearchDocumentStatement(
                sql = sql.rows(sql.perType, highlight),
                params = MapSqlParameterSource(sql.params.values).addValue("perType", perType),
            )
        } else if (size <= 0) {
            // Facets only
            null
        } else {
            SearchDocumentStatement(
                sql = sql.rows(sql.page, highlight),
                params = MapSqlParameterSource(sql.params.values).addValue("offset", offset).addValue("size", size),
            )
        }
        return SearchDocumentStatements(
            // The best rows of each type carry the facets
            facets = if (perType != null) null else SearchDocumentStatement(sql = sql.facets, params = sql.params),
            rows = rows,
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
     * The matches are the documents of the scope matching at least one of the tiers of the query,
     * each one with its tier, the strongest it matches:
     *
     * - `strong` - the exact, prefix and full-text tiers. A narrow projection (ID, type, tier and
     *   recency), so that the tens of thousands of matches of a frequent word are cheap to count
     *   and to sort;
     * - `weak` - the trigram tier, a fallback: only for the types having fewer than
     *   [SearchDocumentRepository.SIMILARITY_FALLBACK_THRESHOLD] strong matches, the scan being
     *   skipped altogether when no type needs it.
     *
     * The count of each type is capped. Only the first matches of each type, up to the cap, are
     * ranked - the candidates: chosen by tier, then recency. Their relevance within their tier
     * (full-text rank, trigram similarity) is computed for them only. The ranking is: tier,
     * relevance within the tier, recency, then the display order of the type. There is no
     * precedence of a type over another before that: an exact build name beats a vague project
     * match.
     *
     * The other columns are read last, for the rows returned only.
     */
    private class SearchDocumentQuery(
        parsed: ParsedSearchQuery,
        scope: SearchDocumentScope,
        countCap: Int,
    ) {

        private val fuzzyTypes = scope.types.filter { it !in scope.nonFuzzyTypes }

        private val similarity = SearchMatchTier.TRIGRAM in parsed.tiers && fuzzyTypes.isNotEmpty()

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
            .addValue("fuzzyTypes", fuzzyTypes)
            .addValue("fuzzyTypeCount", fuzzyTypes.size)
            .addValue("fallbackThreshold", SearchDocumentRepository.SIMILARITY_FALLBACK_THRESHOLD)
            .addValue("countCap", countCap)

        /**
         * Conditions of the tiers other than the trigram one
         */
        private val strongConditions: List<Pair<SearchMatchTier, String>> =
            parsed.tiers.filter { it != SearchMatchTier.TRIGRAM }.map { tier ->
                tier to when (tier) {
                    SearchMatchTier.EXACT -> "d.IDENTIFIERS LIKE :exact"
                    SearchMatchTier.PREFIX -> "(lower(d.TITLE) LIKE :titlePrefix OR d.IDENTIFIERS LIKE :identifierPrefix)"
                    SearchMatchTier.FULL_TEXT -> "d.TSV @@ to_tsquery('simple', :tsq)"
                    SearchMatchTier.TRIGRAM -> error("Not a strong tier")
                }
            }

        private val strong = strongConditions.joinToString(" OR ", prefix = "(", postfix = ")") { it.second }

        private val strongTier = strongConditions.joinToString(
            prefix = "CASE ",
            separator = " ",
            postfix = " END"
        ) { (tier, condition) ->
            "WHEN $condition THEN ${tier.number}"
        }

        /**
         * Relevance of a candidate `r` within its tier, `d` being its document
         */
        private val relevance = parsed.tiers.mapNotNull { tier ->
            when (tier) {
                SearchMatchTier.EXACT, SearchMatchTier.PREFIX -> null
                SearchMatchTier.FULL_TEXT -> "least(ts_rank(d.TSV, to_tsquery('simple', :tsq)), 0.999)"
                SearchMatchTier.TRIGRAM -> "least(greatest(word_similarity(:q, d.TITLE), word_similarity(:q, d.IDENTIFIERS)), 0.999)"
            }?.let { "WHEN ${tier.number} THEN $it" }
        }.takeIf { it.isNotEmpty() }?.joinToString(prefix = "CASE r.TIER ", separator = " ", postfix = " ELSE 0 END") ?: "0"

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

        /**
         * All the matches, narrow: `ID`, `TYPE`, `TIER` and `UPDATED_AT`.
         *
         * The trigram scan is gated by a condition on the strong matches only, which Postgres
         * evaluates once, before the scan: when every fuzzy type has enough strong matches, the
         * trigram indexes are not read at all.
         */
        private val matches = if (similarity) {
            """
                WITH strong AS MATERIALIZED (
                    SELECT d.ID, d.TYPE, $strongTier AS TIER, d.UPDATED_AT
                    FROM SEARCH_DOCUMENTS d
                    WHERE d.TYPE IN (:types)
                    AND $access
                    AND $strong
                ),
                saturated AS MATERIALIZED (
                    SELECT s.TYPE FROM strong s GROUP BY s.TYPE HAVING COUNT(*) >= :fallbackThreshold
                ),
                weak AS MATERIALIZED (
                    SELECT d.ID, d.TYPE, ${SearchMatchTier.TRIGRAM.number} AS TIER, d.UPDATED_AT
                    FROM SEARCH_DOCUMENTS d
                    WHERE (SELECT COUNT(*) FROM saturated x WHERE x.TYPE IN (:fuzzyTypes)) < :fuzzyTypeCount
                    AND d.TYPE IN (:fuzzyTypes)
                    AND d.TYPE NOT IN (SELECT x.TYPE FROM saturated x)
                    AND $access
                    AND (:q <% d.TITLE OR :q <% d.IDENTIFIERS)
                    AND $strong IS NOT TRUE
                ),
                matches AS (
                    SELECT s.ID, s.TYPE, s.TIER, s.UPDATED_AT FROM strong s
                    UNION ALL
                    SELECT w.ID, w.TYPE, w.TIER, w.UPDATED_AT FROM weak w
                )
            """.trimIndent()
        } else {
            """
                WITH matches AS MATERIALIZED (
                    SELECT d.ID, d.TYPE, $strongTier AS TIER, d.UPDATED_AT
                    FROM SEARCH_DOCUMENTS d
                    WHERE d.TYPE IN (:types)
                    AND $access
                    AND $strong
                )
            """.trimIndent()
        }

        /**
         * The candidates: the first matches of each type, by tier then recency, up to the cap,
         * with their relevance and the capped count `N` of their type, `CAPPED` when it is.
         */
        private val candidates = """
            $matches,
            numbered AS MATERIALIZED (
                SELECT m.ID, m.TYPE, m.TIER, m.UPDATED_AT,
                       ROW_NUMBER() OVER (PARTITION BY m.TYPE ORDER BY m.TIER, m.UPDATED_AT DESC, m.ID) AS RN,
                       COUNT(*) OVER (PARTITION BY m.TYPE) AS TYPE_COUNT
                FROM matches m
            ),
            ranked AS MATERIALIZED (
                SELECT n.ID, n.TYPE, n.TIER, n.UPDATED_AT,
                       LEAST(n.TYPE_COUNT, :countCap) AS N,
                       n.TYPE_COUNT > :countCap AS CAPPED
                FROM numbered n
                WHERE n.RN <= :countCap
            ),
            candidates AS (
                SELECT r.ID, r.TYPE, r.TIER, r.UPDATED_AT, r.N, r.CAPPED,
                       $relevance AS RELEVANCE
                FROM ranked r
                JOIN SEARCH_DOCUMENTS d ON d.ID = r.ID
            )
        """.trimIndent()

        private val ranking = "c.TIER, c.RELEVANCE DESC, c.UPDATED_AT DESC, $typeOrder, c.ID"

        /**
         * Score, the higher the better, carrying the tier: 4 for exact, 3 for prefix, [2, 3[ for
         * full-text, [1, 2[ for trigram.
         */
        private val score = "(${SearchMatchTier.entries.size + 1} - c.TIER + c.RELEVANCE)"

        /**
         * Capped count of the matches of each type
         */
        val facets = """
            $matches
            SELECT m.TYPE, LEAST(COUNT(*), :countCap) AS N, COUNT(*) > :countCap AS CAPPED
            FROM matches m
            GROUP BY m.TYPE
        """.trimIndent()

        /**
         * Candidates of a page, best first
         */
        val page = """
            SELECT c.*
            FROM candidates c
            ORDER BY $ranking
            OFFSET :offset LIMIT :size
        """.trimIndent()

        /**
         * Best candidates of each type. Each one carries the capped count of its type, which are
         * the facets, without a second scan of the matches.
         */
        val perType = """
            SELECT c.*
            FROM (
                SELECT c.*,
                       ROW_NUMBER() OVER (PARTITION BY c.TYPE ORDER BY $ranking) AS TYPE_RN
                FROM candidates c
            ) c
            WHERE c.TYPE_RN <= :perType
        """.trimIndent()

        /**
         * Complete query for some [rows][page] of the candidates: their documents, with their
         * `HIGHLIGHT` when asked for.
         *
         * `ts_headline` is expensive: it runs in an outer query, on the rows already selected -
         * never on the other candidates - and only on the free text matching one of the words of
         * the query.
         */
        fun rows(rows: String, highlight: Boolean): String {
            val highlighted = if (highlight) {
                """,
                    CASE WHEN d.TEXT IS NOT NULL AND to_tsvector('simple', d.TEXT) @@ to_tsquery('simple', :anyTsq)
                         THEN ts_headline('simple', translate(d.TEXT, :headlineFrom, :headlineTo), to_tsquery('simple', :anyTsq), :headlineOptions)
                    END AS HIGHLIGHT
                """
            } else {
                ""
            }
            return """
                $candidates
                SELECT d.ID, d.TYPE, d.KEY, d.PROJECT_ID, d.ENTITY_TYPE, d.ENTITY_ID, d.TITLE, d.TEXT, d.DATA, d.UPDATED_AT,
                       c.N, c.CAPPED, $score AS SCORE$highlighted
                FROM ($rows) c
                JOIN SEARCH_DOCUMENTS d ON d.ID = c.ID
                ORDER BY $ranking
            """.trimIndent()
        }
    }

}
