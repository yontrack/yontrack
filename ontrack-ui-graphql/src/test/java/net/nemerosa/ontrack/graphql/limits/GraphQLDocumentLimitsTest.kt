package net.nemerosa.ontrack.graphql.limits

import graphql.parser.Parser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphQLDocumentLimitsTest {

    private val limits = GraphQLDocumentLimits(
        maxAliases = 3,
        maxDirectivesPerLocation = 2,
        isRepeatable = { it == "tag" },
    )

    private fun check(query: String) = limits.check(Parser.parse(query)).firstOrNull()

    @Test
    fun `A query within the limits passes`() {
        assertNull(check("""{ a: projects { name } b: projects { id } }"""))
    }

    @Test
    fun `Aliases up to the limit pass`() {
        assertNull(check("""{ a: x b: x c: x }"""))
    }

    @Test
    fun `Aliases over the limit are reported`() {
        val breach = check("""{ a: x b: x c: x d: x }""")
        assertEquals(GraphQLLimit.ALIASES, breach?.limit)
        assertEquals(4, breach?.value)
        assertEquals(3, breach?.max)
    }

    @Test
    fun `Aliases are counted across the whole document, fragments and nested selections included`() {
        val breach = check(
            """
                query { a: projects { b: name ...f } }
                fragment f on Project { c: id d: name }
            """
        )
        assertEquals(GraphQLLimit.ALIASES, breach?.limit)
        assertEquals(4, breach?.value)
    }

    @Test
    fun `Directives up to the limit on one location pass`() {
        assertNull(check("""query(${'$'}a: Boolean!) { x @include(if: ${'$'}a) @skip(if: ${'$'}a) }"""))
    }

    @Test
    fun `Directives over the limit on one location are reported`() {
        val breach = check("""{ x @a @b @c }""")
        assertEquals(GraphQLLimit.DIRECTIVES, breach?.limit)
        assertEquals(3, breach?.value)
        assertEquals(2, breach?.max)
    }

    @Test
    fun `Directives are counted per location, not per document`() {
        assertNull(check("""{ x @a @b y @a @b ... on Query @a @b { z @a @b } }"""))
    }

    @Test
    fun `A repeated directive is reported even under the limit`() {
        val breach = check("""{ x @a @a }""")
        assertEquals(GraphQLLimit.DIRECTIVES, breach?.limit)
        assertEquals(2, breach?.value)
    }

    @Test
    fun `A repeatable directive may be repeated`() {
        assertNull(check("""{ x @tag @tag }"""))
    }

    @Test
    fun `Directives on operations, fragments and spreads are checked`() {
        assertEquals(GraphQLLimit.DIRECTIVES, check("""query @a @a { x }""")?.limit)
        assertEquals(GraphQLLimit.DIRECTIVES, check("""{ ...f @a @a } fragment f on Query { x }""")?.limit)
        assertEquals(GraphQLLimit.DIRECTIVES, check("""{ ...f } fragment f on Query @a @a { x }""")?.limit)
        assertEquals(GraphQLLimit.DIRECTIVES, check("""query(${'$'}v: Int @a @a) { x }""")?.limit)
    }
}
