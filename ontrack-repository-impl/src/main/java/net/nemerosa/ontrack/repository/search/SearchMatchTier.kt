package net.nemerosa.ontrack.repository.search

/**
 * Ways a query can match a search document, strongest first. The tier is the first criterion of
 * the ranking: any exact match ranks before any prefix match, and so on.
 *
 * @property minLength Minimum length of the query for this tier to run
 */
enum class SearchMatchTier(val minLength: Int) {
    /**
     * One identifier is the query, case-insensitive.
     */
    EXACT(ParsedSearchQuery.MIN_LENGTH),

    /**
     * The query is the start of one identifier or of the title.
     */
    PREFIX(ParsedSearchQuery.MIN_LENGTH),

    /**
     * Every word of the query is the start of a word of the document (title, identifiers, text).
     */
    FULL_TEXT(ParsedSearchQuery.TRIGRAM_MIN_LENGTH),

    /**
     * The query is similar to a word of the title or of the identifiers (typos, substrings).
     */
    TRIGRAM(ParsedSearchQuery.TRIGRAM_MIN_LENGTH),
}
