package net.nemerosa.ontrack.common

/**
 * Canonical (kebab case) form of a dotted property name.
 *
 * Each dot-separated segment is dashed the way Spring's `DataObjectPropertyName.toDashedForm` does
 * it: a dash goes before *every* uppercase character, not only at a lower-to-upper boundary. That
 * distinction only shows up on consecutive capitals, and it matters — Spring binds a field named
 * `apiURL` as `api-u-r-l`, never as `api-url`.
 */
fun String.camelCaseToKebabCase(): String =
    split('.')
        .joinToString(".") { segment -> segment.toDashedForm() }

private fun String.toDashedForm(): String =
    fold(StringBuilder(length)) { result, char ->
        val dashed = if (char == '_') '-' else char
        if (dashed.isUpperCase() && result.isNotEmpty() && result.last() != '-') {
            result.append('-')
        }
        result.append(dashed.lowercaseChar())
    }.toString()

/**
 * Environment variable form of a property name.
 *
 * Spring accepts several environment variable spellings for the same property — for
 * `async-check-interval` both `ASYNC_CHECK_INTERVAL` and the dash-stripped `ASYNCCHECKINTERVAL`
 * bind. This publishes the one derived from the canonical name, which is the form Spring's own
 * documentation uses and the only one that stays readable and consistent with the property name
 * shown next to it.
 */
fun String.camelCaseToEnvironmentName(): String =
    camelCaseToKebabCase()
        .replace(Regex("[^A-Za-z0-9<>*]"), "_")
        .uppercase()
