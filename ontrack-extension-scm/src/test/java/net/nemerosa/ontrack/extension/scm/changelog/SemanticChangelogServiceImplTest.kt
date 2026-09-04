package net.nemerosa.ontrack.extension.scm.changelog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SemanticChangelogServiceImplTest {

    private val service = SemanticChangelogServiceImpl()

    @Test
    fun `No type`() {
        assertEquals(
            SemanticCommit(
                type = null,
                scope = null,
                subject = "ISS-123 Commit with issue",
            ),
            service.parseSemanticCommit("ISS-123 Commit with issue")
        )
    }

    @Test
    fun `Type only`() {
        assertEquals(
            SemanticCommit(
                type = "feat",
                scope = null,
                subject = "Commit for some feature",
            ),
            service.parseSemanticCommit("feat: Commit for some feature")
        )
    }

    @Test
    fun `No type, keeping only the first line`() {
        assertEquals(
            SemanticCommit(
                type = null,
                scope = null,
                subject = "ISS-123 Commit with issue",
            ),
            service.parseSemanticCommit(
                """
                    ISS-123 Commit with issue

                    With a long body explaining at length what the commit does.
                """.trimIndent()
            )
        )
    }

    @Test
    fun `Type only, keeping only the first line`() {
        assertEquals(
            SemanticCommit(
                type = "feat",
                scope = null,
                subject = "Commit for some feature",
            ),
            service.parseSemanticCommit(
                """
                    feat: Commit for some feature

                    With a long body explaining at length what the commit does.
                """.trimIndent()
            )
        )
    }

    @Test
    fun `Type and scope`() {
        assertEquals(
            SemanticCommit(
                type = "feat",
                scope = "My awesome feature",
                subject = "Commit for some feature",
            ),
            service.parseSemanticCommit("feat(My awesome feature): Commit for some feature")
        )
    }

}