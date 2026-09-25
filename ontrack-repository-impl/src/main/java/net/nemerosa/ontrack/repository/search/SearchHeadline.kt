package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.SearchHighlightPart

/**
 * Highlighting of the free text of the search documents, by `ts_headline`.
 *
 * `ts_headline` marks the matches with delimiters of our choice: an HTML delimiter would mix the
 * markup of the highlighting with the one the text may contain. The delimiters are two characters
 * of the Unicode private use area instead, stripped from the text beforehand, and the headline is
 * then [split][parse] on them into [plain text parts][SearchHighlightPart].
 *
 * The parser of `ts_headline` also recognises what looks like HTML tags and replaces them with
 * spaces: a commit message saying `List<String>` would lose its `<String>`. The `<` of the text is
 * therefore [swapped][TRANSLATE_FROM] for a third private character, and restored by [parse].
 */
object SearchHeadline {

    /**
     * Start of a match
     */
    const val START = ''

    /**
     * End of a match
     */
    const val STOP = ''

    /**
     * Stand-in for `<` in the text given to `ts_headline`
     */
    const val LT = '\uE002'

    /**
     * For `translate(TEXT, :from, :to)` before highlighting: `<` becomes [LT], and the private
     * characters the text may already contain are removed.
     */
    const val TRANSLATE_FROM = "<$START$STOP$LT"

    /**
     * See [TRANSLATE_FROM]
     */
    const val TRANSLATE_TO = "$LT"

    /**
     * Options of `ts_headline`: up to three excerpts of the text around its matches, a short text
     * being returned whole.
     */
    const val OPTIONS = "StartSel=\"$START\", StopSel=\"$STOP\", MaxWords=30, MinWords=10, MaxFragments=3, FragmentDelimiter=\" … \""

    /**
     * Splits a headline into its parts, the matches flagged.
     */
    fun parse(headline: String): List<SearchHighlightPart> {
        val parts = mutableListOf<SearchHighlightPart>()
        val current = StringBuilder()
        var match = false
        fun flush() {
            if (current.isNotEmpty()) {
                parts += SearchHighlightPart(current.toString(), match)
                current.clear()
            }
        }
        headline.forEach { c ->
            when (c) {
                START -> {
                    flush()
                    match = true
                }

                STOP -> {
                    flush()
                    match = false
                }

                LT -> current.append('<')

                else -> current.append(c)
            }
        }
        flush()
        return parts
    }

}
