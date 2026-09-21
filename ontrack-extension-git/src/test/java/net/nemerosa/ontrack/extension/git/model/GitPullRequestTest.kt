package net.nemerosa.ontrack.extension.git.model

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitPullRequestTest {

    @Test
    fun `Branches are simplified`() {
        val pr = GitPullRequest(
            id = 1,
            key = "PR-1",
            source = "refs/heads/feature/PRJ-123",
            target = "refs/heads/main",
            title = "My PR",
            status = "open",
            url = "https://issues/PR-1"
        )
        assertEquals("feature/PRJ-123", pr.source)
        assertEquals("main", pr.target)
    }

    @Test
    fun `JSON for valid pull request`() {
        val pr = GitPullRequest(
            id = 1,
            key = "PR-1",
            source = "refs/heads/feature/PRJ-123",
            target = "refs/heads/main",
            title = "My PR",
            status = "open",
            url = "https://issues/PR-1"
        )
        val parsed = pr.asJson().parse<GitPullRequest>()
        assertEquals(1, parsed.id)
        assertEquals("PR-1", parsed.key)
        assertEquals("feature/PRJ-123", parsed.source)
        assertEquals("main", parsed.target)
        assertEquals("My PR", parsed.title)
        assertEquals("open", parsed.status)
        assertEquals("https://issues/PR-1", parsed.url)
        assertEquals(true, parsed.isValid)
    }

    @Test
    fun `JSON for invalid pull request`() {
        val pr = GitPullRequest.invalidPR(1, "PR-1")
        val parsed = pr.asJson().parse<GitPullRequest>()
        assertEquals(1, parsed.id)
        assertEquals("PR-1", parsed.key)
        assertEquals("", parsed.source)
        assertEquals("", parsed.target)
        assertEquals("", parsed.title)
        assertEquals("", parsed.status)
        assertEquals("", parsed.url)
        assertEquals(false, parsed.isValid)
    }

}