package net.nemerosa.ontrack.repository.search

/**
 * How the identifiers of a search document are stored in its `IDENTIFIERS` column: lower-cased,
 * one per line, with a line break before the first and after the last one. An exact match of an
 * identifier is then a `LIKE '%\nvalue\n%'`, and a prefix match a `LIKE '%\nvalue%'`, which the
 * trigram index on the column serves.
 */
object SearchDocumentIdentifiers {

    const val SEPARATOR = "\n"

    private val lineBreaks = "[\\r\\n]+".toRegex()

    fun encode(identifiers: List<String>): String =
        identifiers
            .map { it.replace(lineBreaks, " ").trim().lowercase() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "") { "$it$SEPARATOR" }
            .let { "$SEPARATOR$it" }

}
