package net.nemerosa.ontrack.service.search.perf

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchPerfPlansTest {

    private fun plan(root: String) = """[{"Plan": $root}]"""

    private val bitmapPlan = plan(
        """
        {
          "Node Type": "Aggregate",
          "Plans": [
            {
              "Node Type": "Bitmap Heap Scan",
              "Relation Name": "search_documents",
              "Plans": [
                {
                  "Node Type": "BitmapOr",
                  "Plans": [
                    {"Node Type": "Bitmap Index Scan", "Index Name": "search_documents_ix_identifiers_trgm"},
                    {"Node Type": "Bitmap Index Scan", "Index Name": "search_documents_ix_title_prefix"}
                  ]
                }
              ]
            }
          ]
        }
        """
    )

    private val seqScanPlan = plan(
        """
        {
          "Node Type": "Aggregate",
          "Plans": [
            {"Node Type": "Seq Scan", "Relation Name": "search_documents"}
          ]
        }
        """
    )

    @Test
    fun `nodes of a plan are collected recursively`() {
        val nodes = SearchPerfPlans.nodes(bitmapPlan)
        assertEquals(
            listOf("Aggregate", "Bitmap Heap Scan", "BitmapOr", "Bitmap Index Scan", "Bitmap Index Scan"),
            nodes.map { it.nodeType },
        )
        assertEquals(
            setOf("search_documents_ix_identifiers_trgm", "search_documents_ix_title_prefix"),
            nodes.mapNotNull { it.indexName }.toSet(),
        )
    }

    @Test
    fun `a plan using one of the expected indexes passes`() {
        val check = SearchPerfPlans.check(bitmapPlan, setOf("search_documents_ix_identifiers_trgm"))
        assertTrue(check.passed, check.reason)
        assertEquals(
            listOf("search_documents_ix_identifiers_trgm", "search_documents_ix_title_prefix"),
            check.indexes,
        )
    }

    @Test
    fun `a plan using none of the expected indexes fails`() {
        val check = SearchPerfPlans.check(bitmapPlan, setOf("search_documents_ix_tsv"))
        assertFalse(check.passed)
        assertEquals(
            "none of the expected indexes [search_documents_ix_tsv] is used",
            check.reason,
        )
    }

    @Test
    fun `a plan scanning the whole table fails even when it also uses an expected index`() {
        val plan = plan(
            """
            {
              "Node Type": "Append",
              "Plans": [
                {"Node Type": "Seq Scan", "Relation Name": "search_documents"},
                {"Node Type": "Index Scan", "Index Name": "search_documents_ix_identifiers_trgm", "Relation Name": "search_documents"}
              ]
            }
            """
        )
        val check = SearchPerfPlans.check(plan, setOf("search_documents_ix_identifiers_trgm"))
        assertFalse(check.passed)
        assertEquals("sequential scan of search_documents", check.reason)
    }

    @Test
    fun `a sequential scan of the table fails`() {
        val check = SearchPerfPlans.check(seqScanPlan, SearchPerfPlans.TIER_INDEXES)
        assertFalse(check.passed)
        assertEquals("sequential scan of search_documents", check.reason)
        assertEquals(emptyList(), check.indexes)
    }

    @Test
    fun `a sequential scan of another table is not a failure`() {
        val plan = plan(
            """
            {
              "Node Type": "Nested Loop",
              "Plans": [
                {"Node Type": "Seq Scan", "Relation Name": "projects"},
                {"Node Type": "Index Scan", "Index Name": "search_documents_ix_tsv", "Relation Name": "search_documents"}
              ]
            }
            """
        )
        val check = SearchPerfPlans.check(plan, SearchPerfPlans.TIER_INDEXES)
        assertTrue(check.passed, check.reason)
    }

    /**
     * Partitions of the table (#1888), and their indexes, named after their parents
     */
    private val parents = mapOf(
        "search_documents_p07" to "search_documents",
        "search_documents_p07_tsv_idx" to "search_documents_ix_tsv",
        "search_documents_p07_type_updated_at_id_idx" to "search_documents_ix_type_recency",
    )

    @Test
    fun `a plan on a partition uses the indexes of its parent`() {
        val plan = plan(
            """
            {
              "Node Type": "Bitmap Heap Scan",
              "Relation Name": "search_documents_p07",
              "Plans": [
                {"Node Type": "Bitmap Index Scan", "Index Name": "search_documents_p07_tsv_idx"}
              ]
            }
            """
        )
        val check = SearchPerfPlans.check(plan, SearchPerfPlans.TIER_INDEXES, parents)
        assertTrue(check.passed, check.reason)
        assertEquals(listOf("search_documents_ix_tsv"), check.indexes)
    }

    @Test
    fun `a sequential scan of a partition of the table fails`() {
        val plan = plan(
            """
            {"Node Type": "Seq Scan", "Relation Name": "search_documents_p07"}
            """
        )
        val check = SearchPerfPlans.check(plan, SearchPerfPlans.TIER_INDEXES, parents)
        assertFalse(check.passed)
        assertEquals("sequential scan of search_documents", check.reason)
    }

    @Test
    fun `a sequential scan of a small partition is not a failure`() {
        val plan = plan(
            """
            {
              "Node Type": "Append",
              "Plans": [
                {"Node Type": "Seq Scan", "Relation Name": "search_documents_p09"},
                {"Node Type": "Bitmap Index Scan", "Index Name": "search_documents_p07_tsv_idx"}
              ]
            }
            """
        )
        val check = SearchPerfPlans.check(
            plan, SearchPerfPlans.TIER_INDEXES,
            parents + ("search_documents_p09" to "search_documents"),
            small = setOf("search_documents_p09"),
        )
        assertTrue(check.passed, check.reason)
    }
}

