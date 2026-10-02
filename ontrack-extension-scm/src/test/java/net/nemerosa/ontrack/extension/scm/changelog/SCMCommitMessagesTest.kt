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

class SCMCommitMessagesIssueReferenceTextTest {

    @Test
    fun `Subject only`() {
        assertEquals("#12 Some commit", issueReferenceText("#12 Some commit"))
    }

    @Test
    fun `Body prose is ignored`() {
        assertEquals(
            "#12 Some commit",
            issueReferenceText(
                """
                    #12 Some commit

                    Follow-up of #10, see also #11.
                """.trimIndent()
            )
        )
    }

    @Test
    fun `Each keyword is a trailer, with and without a colon, in any case`() {
        val keywords = listOf(
            "close", "closes", "closed",
            "fix", "fixes", "fixed",
            "resolve", "resolves", "resolved",
            "ref", "refs", "references", "related",
            "issue", "issues",
            "jira-ticket",
        )
        keywords.forEach { keyword ->
            listOf(keyword, keyword.uppercase(), keyword.replaceFirstChar { it.uppercase() }).forEach { spelling ->
                assertEquals(
                    "Subject\n#12",
                    issueReferenceText("Subject\n\nSome prose #10\n\n$spelling #12"),
                    "$spelling without a colon"
                )
                assertEquals(
                    "Subject\n#12",
                    issueReferenceText("Subject\n\nSome prose #10\n\n$spelling: #12"),
                    "$spelling with a colon"
                )
            }
        }
    }

    @Test
    fun `Jira ticket trailer`() {
        assertEquals(
            "Some commit\nABC-123",
            issueReferenceText("Some commit\n\nSome prose about XYZ-1.\n\nJira-Ticket: ABC-123")
        )
    }

    @Test
    fun `Several keys in one trailer`() {
        assertEquals(
            "Subject\n#12, #13\nABC-1 ABC-2",
            issueReferenceText("Subject\n\nCloses #12, #13\nRefs: ABC-1 ABC-2")
        )
    }

    @Test
    fun `A word starting with a keyword is not a trailer`() {
        assertEquals(
            "Subject",
            issueReferenceText("Subject\n\nRefactoring of #12\nFixtures for #13\nIssuer of #14\nClosely #15")
        )
    }

    @Test
    fun `Windows line endings`() {
        assertEquals(
            "Subject\n#13",
            issueReferenceText("Subject\r\n\r\nProse #12\r\nFixes #13\r\n")
        )
    }

    @Test
    fun `Blank message`() {
        assertEquals("", issueReferenceText(""))
    }

    @Test
    fun `Regression - historical mention in the body`() {
        assertEquals(
            "#1713 Delete PromotionRelatedSlotAdmissionRule, which had no caller",
            issueReferenceText(
                """
                    #1713 Delete PromotionRelatedSlotAdmissionRule, which had no caller

                    Its only caller went away with the build promotion info rework of #1236,
                    and the delivery map (#1705) reads the rule config directly because it
                    needs the name that failed to resolve, not a yes/no answer.

                    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                """.trimIndent()
            )
        )
    }

    @Test
    fun `Regression - cross-repository references in the body`() {
        assertEquals(
            "test: ConfigLoaderServiceIT expects the v2 ingestion file of the GitHub test repository",
            issueReferenceText(
                """
                    test: ConfigLoaderServiceIT expects the v2 ingestion file of the GitHub test repository

                    The `v1` and `v1-test` branches of nemerosa/ontrack-github-integration-test move
                    their `.github/ontrack/ingestion.yml` to `version: v2` with
                    `vs-name-normalization: LEGACY` (nemerosa/ontrack-github-integration-test#673,
                    #674), because Yontrack 6 no longer reads the v1 format (#1928). 5.x parses the
                    v2 file into the same configuration; only its `version` differs.

                    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                """.trimIndent()
            )
        )
    }
}
