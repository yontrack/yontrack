package net.nemerosa.ontrack.model.deprecation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The deprecation marker check (ADR 0018), run against small fixture repositories built from
 * `src/test/resources/deprecation/fixtures`. The check of the real repository is
 * [DeprecationMarkersRepositoryTest].
 */
class DeprecationMarkersTest {

    @TempDir
    lateinit var root: File

    private val migrationPage = DeprecationMarkers.MIGRATION_PAGE

    /**
     * Copies a fixture into the temporary repository.
     */
    private fun file(path: String, fixture: String) {
        val content = DeprecationMarkersTest::class.java
            .getResource("/deprecation/fixtures/$fixture")
            ?.readText()
            ?: error("No fixture $fixture")
        File(root, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun check(baseline: Set<String> = emptySet()) = DeprecationMarkers.check(root, baseline)

    private fun violationKeys() = DeprecationMarkers.violations(root).map { it.key }.toSet()

    @Test
    fun `Conforming Kotlin deprecations pass`() {
        file("mod/src/main/java/fixture/Conforming.kt", "Conforming.kt")
        val items = DeprecationMarkers.scan(root)
        assertEquals(
            setOf(
                "kotlin mod/src/main/java/fixture/Conforming.kt#oldThing",
                "kotlin mod/src/main/java/fixture/Conforming.kt#otherOldThing",
                "kotlin mod/src/main/java/fixture/Conforming.kt#TOP",
                "kotlin mod/src/main/java/fixture/Conforming.kt#old thing (legacy)",
            ),
            items.map { it.key }.toSet(),
        )
        assertEquals(
            "Removed in V6. No replacement. See #1235",
            items.first { it.name == "otherOldThing" }.message,
        )
        assertTrue(check().isOk)
    }

    @Test
    fun `A new non-conforming Kotlin deprecation fails the check`() {
        file("mod/src/main/java/fixture/NoMarker.kt", "NoMarker.kt")
        val report = check()
        assertEquals(false, report.isOk)
        val violation = report.newViolations.single()
        assertEquals("kotlin mod/src/main/java/fixture/NoMarker.kt#OldService", violation.key)
        assertEquals("mod/src/main/java/fixture/NoMarker.kt:3", violation.item.location)
        assertEquals(
            listOf("does not start with `Removed in V6.` or `Removed in V7.`", "names no issue (`See #NNNN`)"),
            violation.reasons,
        )
        assertTrue(report.message().contains("mod/src/main/java/fixture/NoMarker.kt:3"))
    }

    @Test
    fun `A deprecation without issue or without replacement fails the check`() {
        file("mod/src/main/java/fixture/Incomplete.kt", "Incomplete.kt")
        val violations = DeprecationMarkers.violations(root).associate { it.item.name to it.reasons }
        assertEquals(listOf("names no issue (`See #NNNN`)"), violations["noIssue"])
        assertEquals(
            listOf("names no replacement (`Use X instead.` or `No replacement.`)"),
            violations["noReplacement"],
        )
    }

    @Test
    fun `Java deprecations are read from the Javadoc tag`() {
        file("mod/src/main/java/fixture/Conforming.java", "Conforming.java")
        file("mod/src/main/java/fixture/NoJavadoc.java", "NoJavadoc.java")
        val items = DeprecationMarkers.scan(root).associateBy { it.key }
        assertEquals(
            "Removed in V7. Use the PUT method instead. See #1234",
            items.getValue("java mod/src/main/java/fixture/Conforming.java#oldEndpoint").message,
        )
        assertEquals(
            setOf("java mod/src/main/java/fixture/NoJavadoc.java#NoJavadoc"),
            violationKeys(),
        )
    }

    @Test
    fun `A GraphQL deprecation is external and must be named on the migration page`() {
        file("mod/src/main/java/fixture/GraphQL.kt", "GraphQL.kt")
        file(migrationPage, "migration-empty.md")
        val violations = DeprecationMarkers.violations(root).associateBy { it.key }
        assertEquals(
            listOf("is external and not named on the migration page"),
            violations.getValue("graphql mod/src/main/java/fixture/GraphQL.kt#oldField").reasons,
        )
        assertEquals(
            listOf(
                "does not start with `Removed in V6.` or `Removed in V7.`",
                "names no replacement (`Use X instead.` or `No replacement.`)",
                "names no issue (`See #NNNN`)",
                "is external and not named on the migration page",
            ),
            violations.getValue("graphql mod/src/main/java/fixture/GraphQL.kt#info").reasons,
        )
        assertEquals(
            listOf("is external and not named on the migration page"),
            violations.getValue("graphql mod/src/main/java/fixture/GraphQL.kt#token").reasons,
        )
        // Once named on the page as `Type.field` or `Type.field(argument)`, only the non-conforming marker is left
        file(migrationPage, "migration-listing.md")
        assertEquals(setOf("graphql mod/src/main/java/fixture/GraphQL.kt#info"), violationKeys())
        // ... the bare `info` in the page does not name it
        assertTrue(
            DeprecationMarkers.violations(root).single().reasons.contains("is external and not named on the migration page")
        )
    }

    @Test
    fun `A runtime warning names an external item which must be on the migration page`() {
        file("mod/src/main/java/fixture/Warning.kt", "Warning.kt")
        file(migrationPage, "migration-empty.md")
        // The item built at runtime is not checked
        assertEquals(setOf("warning mod/src/main/java/fixture/Warning.kt#POST /rest/old"), violationKeys())
        file(migrationPage, "migration-listing.md")
        assertEquals(emptySet(), violationKeys())
    }

    @Test
    fun `Only the main code is scanned for GraphQL deprecations and runtime warnings`() {
        file("mod/src/test/java/fixture/GraphQL.kt", "GraphQL.kt")
        file("mod/src/test/java/fixture/Warning.kt", "Warning.kt")
        assertEquals(emptyList(), DeprecationMarkers.scan(root))
    }

    @Test
    fun `A deprecated KDSL item is external`() {
        file("ontrack-kdsl/src/main/java/fixture/Connector.kt", "Connector.kt")
        file(migrationPage, "migration-empty.md")
        assertEquals(setOf("kotlin ontrack-kdsl/src/main/java/fixture/Connector.kt#oldUpload"), violationKeys())
        file(migrationPage, "migration-listing.md")
        assertEquals(emptySet(), violationKeys())
    }

    @Test
    fun `Frontend deprecations are read from the JSDoc tag`() {
        file("ontrack-web-core/components/helpers.js", "helpers.js")
        val items = DeprecationMarkers.scan(root).associateBy { it.name }
        assertEquals(setOf("useOldHook", "OldComponent"), items.keys)
        assertEquals("Removed in V7. Use `useNewHook` instead. See #1234", items.getValue("useOldHook").message)
        assertEquals(setOf("frontend ontrack-web-core/components/helpers.js#OldComponent"), violationKeys())
    }

    @Test
    fun `Comments, strings, build outputs and test resources are not scanned`() {
        file("mod/src/main/java/fixture/Ignored.kt", "Ignored.kt")
        file("mod/build/generated/NoMarker.kt", "NoMarker.kt")
        file("mod/src/test/resources/NoMarker.kt", "NoMarker.kt")
        file(".claude/worktrees/other/NoMarker.kt", "NoMarker.kt")
        file("ontrack-web-core/node_modules/lib/helpers.js", "helpers.js")
        assertEquals(emptyList(), DeprecationMarkers.scan(root))
    }

    @Test
    fun `The baseline tolerates the items it lists`() {
        file("mod/src/main/java/fixture/NoMarker.kt", "NoMarker.kt")
        file("mod/src/main/java/fixture/Incomplete.kt", "Incomplete.kt")
        val report = check(
            DeprecationMarkers.parseBaseline(
                """
                # Comment
                kotlin mod/src/main/java/fixture/NoMarker.kt#OldService

                kotlin mod/src/main/java/fixture/Incomplete.kt#noIssue
                """.trimIndent()
            )
        )
        assertEquals(
            listOf("kotlin mod/src/main/java/fixture/Incomplete.kt#noReplacement"),
            report.newViolations.map { it.key },
        )
        assertEquals(emptyList(), report.staleEntries)
    }

    @Test
    fun `A baseline entry whose item conforms or is gone must be removed`() {
        file("mod/src/main/java/fixture/Conforming.kt", "Conforming.kt")
        val report = check(
            setOf(
                "kotlin mod/src/main/java/fixture/Conforming.kt#oldThing",
                "kotlin mod/src/main/java/fixture/Removed.kt#removed",
            )
        )
        assertEquals(false, report.isOk)
        assertEquals(emptyList(), report.newViolations)
        assertEquals(
            listOf(
                "kotlin mod/src/main/java/fixture/Conforming.kt#oldThing",
                "kotlin mod/src/main/java/fixture/Removed.kt#removed",
            ),
            report.staleEntries,
        )
        assertTrue(report.message().contains("kotlin mod/src/main/java/fixture/Removed.kt#removed"))
    }

}
