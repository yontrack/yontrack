package net.nemerosa.ontrack.graphql.limits

import graphql.ErrorType
import graphql.introspection.IntrospectionQuery
import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.OntrackConfigProperties.GraphQLLimitsMode
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.ExecutionGraphQlResponse
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphQLLimitsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var executionGraphQlService: ExecutionGraphQlService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val limits get() = ontrackConfigProperties.graphql.limits

    private fun execute(query: String): ExecutionGraphQlResponse =
        asAdmin {
            executionGraphQlService.execute(
                DefaultExecutionGraphQlRequest(query, null, emptyMap(), null, uid("gql_"), null)
            ).block()
        }.let { assertNotNull(it) }

    /**
     * Runs the [query] and checks it is rejected with exactly one error and no data at all.
     */
    private fun assertRejected(query: String, message: String) {
        val response = execute(query)
        assertEquals(
            listOf(message),
            response.errors.map { it.message },
            "One error only, the rejection",
        )
        assertEquals(ErrorType.ExecutionAborted, response.errors.first().errorType)
        assertNull(response.getData<Any>(), "Nothing has run")
    }

    private fun assertAccepted(query: String) {
        val response = execute(query)
        assertEquals(emptyList(), response.errors.map { it.message })
        assertNotNull(response.getData<Any>())
    }

    /**
     * Runs [code] with the default limits, [changed], restoring the configured ones afterwards. The
     * limits are read for every query, so the change applies at once.
     */
    private fun withLimits(changed: OntrackConfigProperties.GraphQLLimitsProperties.() -> Unit, code: () -> Unit) {
        val saved = ontrackConfigProperties.graphql.limits
        ontrackConfigProperties.graphql.limits = OntrackConfigProperties.GraphQLLimitsProperties().apply(changed)
        try {
            code()
        } finally {
            ontrackConfigProperties.graphql.limits = saved
        }
    }

    private fun exceeded(limit: GraphQLLimit, mode: GraphQLLimitsMode): Double =
        meterRegistry.find(GraphQLLimitsMetrics.exceeded)
            .tag("limit", limit.id)
            .tag("mode", mode.id)
            .counter()?.count() ?: 0.0

    @Test
    fun `A hundred and one aliases are rejected by default with one error`() {
        val count = 101
        assertTrue(limits.maxAliases < count, "The default is below the scanner's payload")
        val aliases = (0 until count).joinToString(" ") { "alias$it: __typename" }
        assertRejected(
            "{ $aliases }",
            "The query has $count aliases, more than the ${limits.maxAliases} allowed (ontrack.config.graphql.limits.max-aliases).",
        )
    }

    @Test
    fun `A repeated directive is rejected by default with one error, and not validated once per repetition`() {
        val directives = "@aa".repeat(10)
        assertRejected(
            "{ __typename $directives }",
            "The query has 10 directives on one location, more than the ${limits.maxDirectivesPerLocation} allowed (ontrack.config.graphql.limits.max-directives-per-location).",
        )
    }

    @Test
    fun `Too many directives on one location are rejected by default with one error`() {
        val count = limits.maxDirectivesPerLocation + 1
        val directives = (1..count).joinToString(" ") { "@d$it" }
        assertRejected(
            "{ __typename $directives }",
            "The query has $count directives on one location, more than the ${limits.maxDirectivesPerLocation} allowed (ontrack.config.graphql.limits.max-directives-per-location).",
        )
    }

    @Test
    fun `A query deeper than the limit is rejected with one error`() {
        withLimits({ maxDepth = 3 }) {
            assertAccepted("{ projects { branches { name } } }")
            assertRejected(
                "{ projects { branches { project { name } } } }",
                "The query has a depth of 4, more than the 3 allowed (ontrack.config.graphql.limits.max-depth).",
            )
        }
    }

    @Test
    fun `A query more complex than the limit is rejected with one error`() {
        withLimits({ maxComplexity = 3 }) {
            assertAccepted("{ projects { id name } }")
            assertRejected(
                "{ projects { id name description } }",
                "The query has a complexity of 4, more than the 3 allowed (ontrack.config.graphql.limits.max-complexity).",
            )
        }
    }

    @Test
    fun `A breach is counted`() {
        val before = exceeded(GraphQLLimit.DEPTH, GraphQLLimitsMode.ENFORCE)
        withLimits({ maxDepth = 1 }) {
            execute("{ projects { name } }")
        }
        assertEquals(1.0, exceeded(GraphQLLimit.DEPTH, GraphQLLimitsMode.ENFORCE) - before)
    }

    @Test
    fun `In warning mode, a query over the limits runs and the breach is counted`() {
        val before = exceeded(GraphQLLimit.ALIASES, GraphQLLimitsMode.WARN)
        withLimits({
            mode = GraphQLLimitsMode.WARN
            maxAliases = 1
            maxDirectivesPerLocation = 0
            maxDepth = 1
            maxComplexity = 1
        }) {
            assertAccepted("{ a: projects { id } b: projects @include(if: true) { name } }")
        }
        assertEquals(1.0, exceeded(GraphQLLimit.ALIASES, GraphQLLimitsMode.WARN) - before)
    }

    @Test
    fun `The standard introspection query passes the default limits`() {
        assertAccepted(IntrospectionQuery.INTROSPECTION_QUERY)
    }

    @Test
    fun `An ordinary query passes the default limits`() {
        val project = asAdmin {
            project().apply { branch().build() }
        }
        assertAccepted(
            """
                {
                    projects(id: ${project.id}) {
                        name
                        branches {
                            name
                            builds {
                                name
                                promotionRuns { promotionLevel { name } }
                                validationRuns { validationStamp { name } lastStatus { statusID { id } } }
                            }
                        }
                    }
                }
            """
        )
    }
}
