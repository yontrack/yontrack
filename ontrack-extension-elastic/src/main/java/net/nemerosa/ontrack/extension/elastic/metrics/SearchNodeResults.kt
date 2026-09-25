package net.nemerosa.ontrack.extension.elastic.metrics

/**
 * Results being returned by a search.
 *
 * @property items List of items actually returned
 * @property offset Offset of the result list
 * @property total Total number of matches
 * @property message Any message associated with the search
 */
class SearchNodeResults(
        val items: List<SearchResultNode>,
        val offset: Int,
        val total: Int,
        val message: String?
)
