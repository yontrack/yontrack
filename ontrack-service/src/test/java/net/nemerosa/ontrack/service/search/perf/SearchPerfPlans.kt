package net.nemerosa.ontrack.service.search.perf

import net.nemerosa.ontrack.json.parseAsJson
import tools.jackson.databind.JsonNode

/**
 * Reading of the plans returned by `EXPLAIN (FORMAT JSON)`.
 */
object SearchPerfPlans {

    const val TABLE = "search_documents"

    const val IX_TSV = "search_documents_ix_tsv"
    const val IX_IDENTIFIERS_TRGM = "search_documents_ix_identifiers_trgm"
    const val IX_TITLE_TRGM = "search_documents_ix_title_trgm"
    const val IX_TITLE_PREFIX = "search_documents_ix_title_prefix"

    /**
     * The indexes serving the tiers of a query (V85)
     */
    val TIER_INDEXES = setOf(IX_TSV, IX_IDENTIFIERS_TRGM, IX_TITLE_TRGM, IX_TITLE_PREFIX)

    /**
     * The index only the trigram tier reads, the fallback of the fuzzy types (#1888): the other
     * tiers read the trigram index of the identifiers too, for their `LIKE`s
     */
    val TRIGRAM_INDEXES = setOf(IX_TITLE_TRGM)

    private val indexNodeTypes = setOf("Index Scan", "Index Only Scan", "Bitmap Index Scan")

    data class PlanNode(
        val nodeType: String,
        val relationName: String?,
        val indexName: String?,
    )

    /**
     * Result of the check of a plan.
     *
     * @property passed Whether the plan uses one of the expected indexes and never scans the whole table
     * @property reason Why it does not pass
     * @property indexes Indexes the plan uses, in the order they appear in it
     */
    data class PlanCheck(
        val passed: Boolean,
        val reason: String?,
        val indexes: List<String>,
    )

    /**
     * All the nodes of a plan, depth first.
     */
    fun nodes(plan: String): List<PlanNode> {
        val root = plan.parseAsJson().path(0).path("Plan")
        val nodes = mutableListOf<PlanNode>()
        collect(root, nodes)
        return nodes
    }

    private fun collect(node: JsonNode, nodes: MutableList<PlanNode>) {
        if (node.isMissingNode || node.isNull) return
        nodes += PlanNode(
            nodeType = node.path("Node Type").asString(),
            relationName = node.path("Relation Name").takeIf { it.isString }?.asString(),
            indexName = node.path("Index Name").takeIf { it.isString }?.asString(),
        )
        node.path("Plans").forEach { child -> collect(child, nodes) }
    }

    /**
     * Checks that a plan uses at least one of the [expected] indexes, and that it never scans the
     * whole search documents table.
     */
    fun check(plan: String, expected: Set<String>): PlanCheck {
        val nodes = nodes(plan)
        val indexes = nodes
            .filter { it.nodeType in indexNodeTypes }
            .mapNotNull { it.indexName }
            .distinct()
        val reason = when {
            nodes.any { it.nodeType == "Seq Scan" && it.relationName == TABLE } ->
                "sequential scan of $TABLE"

            indexes.none { it in expected } ->
                "none of the expected indexes ${expected.sorted()} is used"

            else -> null
        }
        return PlanCheck(passed = reason == null, reason = reason, indexes = indexes)
    }
}
