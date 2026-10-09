package net.nemerosa.ontrack.graphql.limits

import graphql.language.DirectivesContainer
import graphql.language.Document
import graphql.language.Field
import graphql.language.Node

/**
 * Limits checked on the parsed document, before it is validated: a validation reports one error per
 * offending node, so a document repeating a directive a hundred times would cost a hundred errors
 * before it was rejected.
 *
 * The whole document is counted, whatever the operation which runs: a document is bounded by the
 * token limit of the parser, and a document with several operations is no reason to allow more.
 *
 * @property maxAliases Maximum number of aliases in the document
 * @property maxDirectivesPerLocation Maximum number of directives on one location (field, fragment,
 * spread, operation, variable)
 * @property isRepeatable Whether a directive, by name, may be repeated on one location. An unknown
 * directive may not.
 */
class GraphQLDocumentLimits(
    private val maxAliases: Int,
    private val maxDirectivesPerLocation: Int,
    private val isRepeatable: (name: String) -> Boolean,
) {

    /**
     * Breaches of the limits by the [document], none when it is within them.
     */
    fun check(document: Document): List<GraphQLLimitBreach> {
        val measure = Measure()
        measure.walk(document)
        return listOfNotNull(
            measure.aliases.takeIf { it > maxAliases }?.let {
                GraphQLLimitBreach.aliases(it, maxAliases)
            },
            measure.directives.takeIf { it > maxDirectivesPerLocation }?.let {
                GraphQLLimitBreach.directives(it, maxDirectivesPerLocation)
            } ?: measure.repeated?.let { (name, count) ->
                GraphQLLimitBreach.repeatedDirective(name, count, maxDirectivesPerLocation)
            },
        )
    }

    private inner class Measure {

        var aliases = 0

        /**
         * Largest number of directives on one location
         */
        var directives = 0

        /**
         * First non-repeatable directive repeated on one location, with its count there
         */
        var repeated: Pair<String, Int>? = null

        fun walk(node: Node<*>) {
            if (node is Field && node.alias != null) {
                aliases++
            }
            if (node is DirectivesContainer<*>) {
                val list = node.directives
                directives = maxOf(directives, list.size)
                if (repeated == null && list.size > 1) {
                    repeated = list.groupingBy { it.name }.eachCount()
                        .entries
                        .firstOrNull { (name, count) -> count > 1 && !isRepeatable(name) }
                        ?.toPair()
                }
            }
            node.children.forEach { walk(it) }
        }
    }
}
