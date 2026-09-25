package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.annotation.JsonIgnore
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Result for a research
 */
class SearchResult
@JvmOverloads
constructor(
        /**
         * Short title
         */
        val title: String,
        /**
         * Description linked to the item being found
         */
        val description: String,
        /**
         * Score for the search
         */
        val accuracy: Double,
        /**
         * Type of result
         */
        val type: SearchResultType,
        /**
         * Meta-data which can be used internally
         */
        @APIDescription("Meta-data associated with the result")
        @get:JsonIgnore
        val data: Map<String, *>? = null,
        /**
         * Excerpt of the free text where it matches the query, when asked for
         */
        @APIDescription("Excerpt of the free text of the result where it matches the query")
        val highlight: List<SearchHighlightPart>? = null,
) {
    companion object {
        const val SEARCH_RESULT_ITEM = "item"
        const val SEARCH_RESULT_ENTITY = "entity"
        const val SEARCH_RESULT_ENTITY_TYPE = "entityType"
        const val SEARCH_RESULT_ENTITY_ID = "entityId"
        const val SEARCH_RESULT_PROJECT = "project"
        const val SEARCH_RESULT_BRANCH = "branch"
        const val SEARCH_RESULT_BUILD = "build"
        const val SEARCH_RESULT_BUILD_RELEASE = "release"
    }
}
