package net.nemerosa.ontrack.demo.seed

import java.io.File
import kotlin.test.Test

/**
 * The changelog source and the changelog project have to agree on one thing: the order the
 * entries arrive in is the order of their times. Nothing states that contract on either
 * side alone, so it is checked here, across the seam where it was once broken.
 */
class ChangelogProjectTest {

    /** What %x1f expands to in the git format the source asks for. */
    private val separator = ''

    private fun line(id: String, message: String, time: String) = "$id$separator$message$separator$time"

    private fun datasetFrom(vararg lines: String) =
        DemoContent.dataset(
            GitChangelogSource(
                directory = File("."),
                git = { command -> if (command.first() == "describe") "5.3.0" else lines.joinToString("\n") },
            ).entries()
        )

    /**
     * A pull request rebased on merge carries a commit date later than the commits that
     * landed while it was open, so `git log` prints it above them while its own time is
     * below theirs. Seeded in that order, the changelog branch declares its builds newest
     * first and the dataset is rejected (demo-smoke, 2026-09-05).
     */
    @Test
    fun `a rebased pull request still seeds a valid dataset`() {
        datasetFrom(
            line("510acda0", "Merged pull request", "2026-09-04T19:45:21+02:00"),
            line("6fc965c9", "Landed while it was open", "2026-09-04T20:36:57+02:00"),
        ).validate()
    }

    @Test
    fun `a changelog in order still seeds a valid dataset`() {
        datasetFrom(
            line("6fc965c9", "Landed second", "2026-09-04T20:36:57+02:00"),
            line("510acda0", "Landed first", "2026-09-04T19:45:21+02:00"),
        ).validate()
    }
}
