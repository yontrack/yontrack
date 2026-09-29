package net.nemerosa.ontrack.common

fun includes(text: String, includes: String) =
    FilterHelper.includes(text, listOf(includes))

/**
 * Checks the [text] against an inclusion and an exclusion regular expression. A blank
 * [excludes] excludes nothing.
 */
fun includes(text: String, includes: String, excludes: String) =
    FilterHelper.includes(text, listOf(includes), listOfNotNull(excludes.takeIf { it.isNotBlank() }))

fun excludes(text: String, includes: String, excludes: String) =
    !includes(text, includes, excludes)
