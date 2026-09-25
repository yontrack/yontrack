package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.SearchQueryRequest

/**
 * A search query, parsed into what each [tier][SearchMatchTier] needs to match it.
 *
 * @property text Query, trimmed, lower-cased, with its white spaces collapsed
 * @property tiers Tiers which run for this query
 * @property exactPattern `LIKE` pattern matching one of the encoded
 * [identifiers][SearchDocumentIdentifiers] exactly
 * @property identifierPrefixPattern `LIKE` pattern matching the start of one of the encoded
 * [identifiers][SearchDocumentIdentifiers]
 * @property titlePrefixPattern `LIKE` pattern matching the start of the lower-cased title
 * @property tsQuery Text for `to_tsquery('simple', ...)`: each word of the query as a quoted
 * prefix, all of them required
 * @property anyWordTsQuery Text for `to_tsquery('simple', ...)`: each word of the query as a quoted
 * prefix, any of them - what the free text is highlighted with
 */
class ParsedSearchQuery private constructor(
    val text: String,
    val tiers: List<SearchMatchTier>,
    val exactPattern: String,
    val identifierPrefixPattern: String,
    val titlePrefixPattern: String,
    val tsQuery: String,
    val anyWordTsQuery: String,
) {

    companion object {

        /**
         * Minimum length of a query
         */
        const val MIN_LENGTH = SearchQueryRequest.MIN_QUERY_LENGTH

        /**
         * Minimum length of a query for the full-text and trigram tiers, the ones which cannot
         * run on the B-tree and trigram indexes on a two-character query.
         */
        const val TRIGRAM_MIN_LENGTH = 3

        private val whitespaces = "\\s+".toRegex()

        /**
         * Parses a query.
         *
         * @return `null` if the query is too short to be searched
         */
        fun parse(query: String): ParsedSearchQuery? {
            val text = query.trim().lowercase().replace(whitespaces, " ")
            if (text.length < MIN_LENGTH) {
                return null
            }
            val like = escapeLike(text)
            val tsWords = text.split(" ").map { word -> "'${escapeTsQuery(word)}':*" }
            return ParsedSearchQuery(
                text = text,
                tiers = SearchMatchTier.entries.filter { text.length >= it.minLength },
                exactPattern = "%${SearchDocumentIdentifiers.SEPARATOR}$like${SearchDocumentIdentifiers.SEPARATOR}%",
                identifierPrefixPattern = "%${SearchDocumentIdentifiers.SEPARATOR}$like%",
                titlePrefixPattern = "$like%",
                tsQuery = tsWords.joinToString(" & "),
                anyWordTsQuery = tsWords.joinToString(" | "),
            )
        }

        /**
         * Escapes the wildcards of `LIKE`, whose default escape character is the backslash.
         */
        private fun escapeLike(value: String) =
            value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

        /**
         * Escapes a word to be quoted in a `tsquery`.
         */
        private fun escapeTsQuery(value: String) =
            value
                .replace("\\", "\\\\")
                .replace("'", "''")
    }

}
