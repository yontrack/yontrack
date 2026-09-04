package net.nemerosa.ontrack.extension.scm.changelog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SCMCommitMessagesTest {

    @Test
    fun `Short message is left untouched`() {
        assertEquals("Some commit", shortCommitMessage("Some commit"))
    }

    @Test
    fun `Only the first line is kept`() {
        assertEquals(
            "Some commit",
            shortCommitMessage(
                """
                    Some commit

                    With a very long body, explaining at length what the commit does,
                    as agents like to write them.
                """.trimIndent()
            )
        )
    }

    @Test
    fun `First line is kept for Windows line endings`() {
        assertEquals("Some commit", shortCommitMessage("Some commit\r\nWith a body"))
    }

    @Test
    fun `Message at the maximum length is left untouched`() {
        val message = "a".repeat(100)
        assertEquals(message, shortCommitMessage(message))
    }

    @Test
    fun `Message longer than the maximum length is truncated, ellipsis included`() {
        val short = shortCommitMessage("a".repeat(101))
        assertEquals("a".repeat(99) + "…", short)
        assertEquals(100, short.length)
    }

    @Test
    fun `Trailing spaces are trimmed before the ellipsis`() {
        assertEquals(
            "Some…",
            shortCommitMessage("Some   commit which is too long", maxLength = 8)
        )
    }

    @Test
    fun `No truncation when the maximum length is zero, but still the first line only`() {
        val message = "a".repeat(200)
        assertEquals(message, shortCommitMessage(message, maxLength = 0))
        assertEquals("Some commit", shortCommitMessage("Some commit\nWith a body", maxLength = 0))
    }

    @Test
    fun `No truncation when the maximum length is negative`() {
        val message = "a".repeat(200)
        assertEquals(message, shortCommitMessage(message, maxLength = -1))
    }

    @Test
    fun `Blank message`() {
        assertEquals("", shortCommitMessage(""))
    }
}
