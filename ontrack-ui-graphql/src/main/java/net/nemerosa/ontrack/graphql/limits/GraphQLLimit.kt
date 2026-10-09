package net.nemerosa.ontrack.graphql.limits

/**
 * Limits on the cost of a GraphQL query, each one set by a property of
 * `ontrack.config.graphql.limits`.
 *
 * @property id Identifier of the limit, as a metric tag
 * @property property Name of the property setting the limit
 */
enum class GraphQLLimit(
    val id: String,
    val property: String,
) {

    ALIASES("aliases", "max-aliases"),
    DIRECTIVES("directives", "max-directives-per-location"),
    DEPTH("depth", "max-depth"),
    COMPLEXITY("complexity", "max-complexity"),

}

/**
 * A query going over one of the [GraphQLLimit]s.
 *
 * @property limit Limit the query goes over
 * @property value What the query measures
 * @property max What the limit allows
 * @property message Why the query is rejected, for the client
 */
data class GraphQLLimitBreach(
    val limit: GraphQLLimit,
    val value: Int,
    val max: Int,
    val message: String,
) {
    companion object {

        private fun allowed(limit: GraphQLLimit, max: Int) =
            "more than the $max allowed (ontrack.config.graphql.limits.${limit.property})."

        fun aliases(value: Int, max: Int) = GraphQLLimitBreach(
            GraphQLLimit.ALIASES, value, max,
            "The query has $value aliases, ${allowed(GraphQLLimit.ALIASES, max)}",
        )

        fun directives(value: Int, max: Int) = GraphQLLimitBreach(
            GraphQLLimit.DIRECTIVES, value, max,
            "The query has $value directives on one location, ${allowed(GraphQLLimit.DIRECTIVES, max)}",
        )

        fun repeatedDirective(name: String, value: Int, max: Int) = GraphQLLimitBreach(
            GraphQLLimit.DIRECTIVES, value, max,
            "The query repeats the @$name directive on one location, which it is not declared repeatable for.",
        )

        fun depth(value: Int, max: Int) = GraphQLLimitBreach(
            GraphQLLimit.DEPTH, value, max,
            "The query has a depth of $value, ${allowed(GraphQLLimit.DEPTH, max)}",
        )

        fun complexity(value: Int, max: Int) = GraphQLLimitBreach(
            GraphQLLimit.COMPLEXITY, value, max,
            "The query has a complexity of $value, ${allowed(GraphQLLimit.COMPLEXITY, max)}",
        )
    }
}
