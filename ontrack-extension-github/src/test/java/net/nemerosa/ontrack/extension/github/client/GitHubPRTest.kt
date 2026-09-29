package net.nemerosa.ontrack.extension.github.client

import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitHubPRTest {

    @Test
    fun `Reading a PR as returned by the GitHub API`() {
        val pr = """
            {
              "number": 12,
              "state": "closed",
              "merged": true,
              "mergeable": null,
              "mergeable_state": "unknown",
              "html_url": "https://github.com/yontrack/yontrack/pull/12",
              "title": "Some feature",
              "head": { "ref": "feature/some", "sha": "abc" },
              "base": { "ref": "main", "sha": "def" },
              "user": { "login": "someone" }
            }
        """.parseAsJson().parse<GitHubPR>()
        assertEquals(12, pr.number)
        assertEquals("Some feature", pr.title)
        assertEquals("feature/some", pr.head?.ref)
        assertEquals("main", pr.base?.ref)
        assertEquals(SCMPullRequestStatus.MERGED, pr.status)
    }

}
