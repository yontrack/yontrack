package net.nemerosa.ontrack.demo.seed

import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitChangelogSourceTest {

    /** What %x1f expands to in the git format the source asks for. */
    private val separator = '\u001F'

    private fun line(id: String, message: String, time: String) = "$id$separator$message$separator$time"

    @Test
    fun `parses the log into entries, in UTC`() {
        val entries = GitChangelogSource.parseLog(
            listOf(
                line("61982ec", "Remove .mcp.json from the repository", "2026-09-01T07:30:00+02:00"),
                line("0e01b33", "#1664 Drive the demo deployment", "2026-08-31T18:00:00Z"),
            ).joinToString("\n")
        )

        assertEquals(
            listOf(
                ChangelogEntry("61982ec", "Remove .mcp.json from the repository", LocalDateTime.of(2026, 9, 1, 5, 30)),
                ChangelogEntry("0e01b33", "#1664 Drive the demo deployment", LocalDateTime.of(2026, 8, 31, 18, 0)),
            ),
            entries,
        )
    }

    @Test
    fun `a commit subject containing the separator does not shift the fields`() {
        val entries = GitChangelogSource.parseLog(
            line("61982ec", "Fix: a | b, x\ty", "2026-09-01T07:30:00Z")
        )

        assertEquals(listOf("Fix: a | b, x\ty"), entries.map { it.message })
    }

    @Test
    fun `malformed lines are skipped rather than failing the reset`() {
        val entries = GitChangelogSource.parseLog(
            listOf(
                "not a log line at all",
                line("61982ec", "Good one", "2026-09-01T07:30:00Z"),
                line("0e01b33", "Bad date", "yesterday"),
                "",
            ).joinToString("\n")
        )

        assertEquals(listOf("61982ec"), entries.map { it.id })
    }

    @Test
    fun `no release tag yields no entry and one warning`() {
        val warnings = mutableListOf<String>()
        val source = GitChangelogSource(
            directory = File("."),
            warn = { warnings += it },
            git = { null },
        )

        assertEquals(emptyList(), source.entries())
        assertEquals(1, warnings.size)
        assertTrue("release tag" in warnings.first())
    }

    @Test
    fun `an unreadable log yields no entry and one warning`() {
        val warnings = mutableListOf<String>()
        val source = GitChangelogSource(
            directory = File("."),
            warn = { warnings += it },
            git = { command -> if (command.first() == "describe") "5.2.2\n" else null },
        )

        assertEquals(emptyList(), source.entries())
        assertEquals(listOf(true), warnings.map { "5.2.2" in it })
    }

    @Test
    fun `entries come back newest first whatever order the log is in`() {
        val log = listOf(
            line("510acda0", "Rebased on merge", "2026-09-04T19:45:21+02:00"),
            line("6fc965c9", "Landed while it was open", "2026-09-04T20:36:57+02:00"),
        ).joinToString("\n")
        val source = GitChangelogSource(
            directory = File("."),
            git = { command -> if (command.first() == "describe") "5.3.0" else log },
        )

        assertEquals(listOf("6fc965c9", "510acda0"), source.entries().map { it.id })
    }

    @Test
    fun `the most recent commits are kept, not the ones the log happens to print first`() {
        val log = listOf(
            line("old", "Rebased on merge", "2026-09-01T10:00:00Z"),
            line("new", "Landed while it was open", "2026-09-02T10:00:00Z"),
        ).joinToString("\n")
        val source = GitChangelogSource(
            directory = File("."),
            max = 1,
            git = { command -> if (command.first() == "describe") "5.3.0" else log },
        )

        assertEquals(listOf("new"), source.entries().map { it.id })
    }

    @Test
    fun `only the most recent commits are kept`() {
        val log = (1..10).joinToString("\n") { line("commit$it", "Message $it", "2026-09-01T07:30:00Z") }
        val source = GitChangelogSource(
            directory = File("."),
            max = 3,
            git = { command -> if (command.first() == "describe") "5.2.2" else log },
        )

        assertEquals(listOf("commit1", "commit2", "commit3"), source.entries().map { it.id })
    }
}
