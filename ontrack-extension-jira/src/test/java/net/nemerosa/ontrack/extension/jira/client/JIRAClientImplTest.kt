package net.nemerosa.ontrack.extension.jira.client

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.JIRAFixtures
import net.nemerosa.ontrack.extension.jira.model.JIRALink
import net.nemerosa.ontrack.extension.jira.model.JIRAStatus
import net.nemerosa.ontrack.extension.jira.model.JIRAVersion
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JIRAClientImplTest {

    @Test
    fun parseFromJIRA() {
        val ldt = JIRAClientImpl.parseFromJIRA("2014-06-05T14:39:51.943+0000")
        assertEquals(2014, ldt.year)
        assertEquals(6, ldt.monthValue)
        assertEquals(5, ldt.dayOfMonth)
        assertEquals(14, ldt.hour)
        assertEquals(51, ldt.second)
    }

    @Test
    fun toIssue() {
        // Configuration to test with
        val config = config()
        // Issue to parse
        val node = ObjectMapper().readTree(this::class.java.getResource("/issue.json")!!.readText())
        // Parsing the issue
        val issue = JIRAClientImpl.toIssue(config, node)
        // Checking the issue
        assertNotNull(issue) {
            assertEquals("PRJ-136", issue.key)
            assertEquals("Issue summary", issue.summary)
            assertEquals(LocalDateTime.of(2014, 6, 18, 15, 12, 52, 369000000), issue.updateTime)
            assertEquals(JIRAStatus("Closed", "http://jira/images/icons/statuses/closed.png"), issue.status)
            assertEquals(listOf(JIRAVersion("1.2", false)), issue.fixVersions)
            assertEquals("dcoraboeuf", issue.assignee)
            assertEquals(listOf(JIRAVersion("1.0", true)), issue.affectedVersions)
        }
    }

    @Test
    fun `toIssue with one inward link`() {
        // Configuration to test with
        val config = config()
        // Issue to parse
        val node = ObjectMapper().readTree(this::class.java.getResource("/issue-link-inward.json")!!.readText())
        // Parsing the issue
        val issue = JIRAClientImpl.toIssue(config, node)
        // Checking the issue
        assertNotNull(issue) {
            assertEquals("PRJ-136", issue.key)
            assertEquals("Issue summary", issue.summary)
            assertEquals(LocalDateTime.of(2014, 6, 18, 15, 12, 52, 369000000), issue.updateTime)
            assertEquals(JIRAStatus("Closed", "http://jira/images/icons/statuses/closed.png"), issue.status)
            assertEquals(listOf(JIRAVersion("1.2", false)), issue.fixVersions)
            assertEquals("dcoraboeuf", issue.assignee)
            assertEquals(listOf(JIRAVersion("1.0", true)), issue.affectedVersions)
            assertEquals(
                listOf(
                    JIRALink(
                        "PRJ-900",
                        "http://jira/browse/PRJ-900",
                        JIRAStatus("Open", "http://jira/images/icons/statuses/open.png"),
                        "Blocks",
                        "is blocked by"
                    )
                ),
                issue.links
            )
        }
    }

    @Test
    fun `toIssue with one outward link`() {
        // Configuration to test with
        val config = config()
        // Issue to parse
        val node = ObjectMapper().readTree(this::class.java.getResource("/issue-link-outward.json")!!.readText())
        // Parsing the issue
        val issue = JIRAClientImpl.toIssue(config, node)
        // Checking the issue
        assertNotNull(issue) {
            assertEquals("PRJ-136", issue.key)
            assertEquals("Issue summary", issue.summary)
            assertEquals(LocalDateTime.of(2014, 6, 18, 15, 12, 52, 369000000), issue.updateTime)
            assertEquals(JIRAStatus("Closed", "http://jira/images/icons/statuses/closed.png"), issue.status)
            assertEquals(listOf(JIRAVersion("1.2", false)), issue.fixVersions)
            assertEquals("dcoraboeuf", issue.assignee)
            assertEquals(listOf(JIRAVersion("1.0", true)), issue.affectedVersions)
            assertEquals(
                listOf(
                    JIRALink(
                        "PRJ-900",
                        "http://jira/browse/PRJ-900",
                        JIRAStatus("Open", "http://jira/images/icons/statuses/open.png"),
                        "Blocks",
                        "blocks"
                    )
                ),
                issue.links
            )
        }
    }

    @Test
    fun `toIssue with two links`() {
        // Configuration to test with
        val config = config()
        // Issue to parse
        val node = ObjectMapper().readTree(this::class.java.getResource("/issue-link-both.json")!!.readText())
        // Parsing the issue
        val issue = JIRAClientImpl.toIssue(config, node)
        // Checking the issue
        assertNotNull(issue) {
            assertEquals("PRJ-136", issue.key)
            assertEquals("Issue summary", issue.summary)
            assertEquals(LocalDateTime.of(2014, 6, 18, 15, 12, 52, 369000000), issue.updateTime)
            assertEquals(JIRAStatus("Closed", "http://jira/images/icons/statuses/closed.png"), issue.status)
            assertEquals(listOf(JIRAVersion("1.2", false)), issue.fixVersions)
            assertEquals("dcoraboeuf", issue.assignee)
            assertEquals(listOf(JIRAVersion("1.0", true)), issue.affectedVersions)
            assertEquals(
                listOf(
                    JIRALink(
                        "PRJ-900",
                        "http://jira/browse/PRJ-900",
                        JIRAStatus("Open", "http://jira/images/icons/statuses/open.png"),
                        "Blocks",
                        "blocks"
                    ),
                    JIRALink(
                        "PRJ-901",
                        "http://jira/browse/PRJ-901",
                        JIRAStatus("Open", "http://jira/images/icons/statuses/open.png"),
                        "Blocks",
                        "is blocked by"
                    ),
                ),
                issue.links
            )
        }
    }

    @Test
    fun `Forbidden access returns a null issue`() {
        val template = mockk<RestTemplate>()
        val client = JIRAClientImpl(template)
        val config = JIRAFixtures.jiraConfiguration()

        every {
            template.getForObject<JsonNode>("/rest/api/2/issue/XXX-1?expand=names")
        } throws HttpClientErrorException.create(
            HttpStatus.FORBIDDEN,
            "Forbidden",
            HttpHeaders(),
            ByteArray(0),
            null
        )

        assertNull(client.getIssue("XXX-1", config))
    }

    @Test
    fun `Not found returns a null issue`() {
        val template = mockk<RestTemplate>()
        val client = JIRAClientImpl(template)
        val config = JIRAFixtures.jiraConfiguration()

        every {
            template.getForObject<JsonNode>("/rest/api/2/issue/XXX-1?expand=names")
        } throws HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
            "Not found",
            HttpHeaders(),
            ByteArray(0),
            null
        )

        assertNull(client.getIssue("XXX-1", config))
    }

    private fun config() = JIRAConfiguration(
        name = "Test",
        url = "http://jira",
        user = "user",
        password = "secret",
        include = emptyList(),
        exclude = emptyList()
    )

}
