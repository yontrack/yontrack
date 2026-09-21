package net.nemerosa.ontrack.json

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Yontrack is on Jackson 3 (`tools.jackson`), and no source, main or test, of any module goes back to
 * Jackson 2 (#1843, ADR 0016). The annotations are the exception: Jackson 3 keeps
 * `com.fasterxml.jackson.annotation`.
 *
 * A Jackson 2 type compiles as long as a library brings Jackson 2 along, and then fails at runtime,
 * or exchanges nodes that Jackson 3 writes as beans.
 */
class NoJackson2GuardTest {

    private val root: File = File("..").canonicalFile

    /**
     * Jackson 2 packages other than the annotations, written in two parts so that this file does
     * not match itself.
     */
    private val jackson2 = Regex(
        "com" + "\\.fasterxml\\.jackson\\.(core|databind|datatype|dataformat|module)\\b"
    )

    /**
     * Build outputs, and hidden directories: in the main working copy, `.claude/worktrees` holds
     * the checkouts of other branches.
     */
    private fun File.isSkipped() = name in setOf("build", "node_modules") || name.startsWith(".")

    @Test
    fun `No source refers to Jackson 2`() {
        assertTrue(File(root, "settings.gradle.kts").exists(), "Root of the build found at $root")
        val offenders = root.walkTopDown()
            .onEnter { it == root || !it.isSkipped() }
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (jackson2.containsMatchIn(line)) {
                        "${file.relativeTo(root)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        assertEquals(
            emptyList(),
            offenders,
            "Jackson 2 is referred to - use `tools.jackson` instead (ADR 0016)",
        )
    }

}
