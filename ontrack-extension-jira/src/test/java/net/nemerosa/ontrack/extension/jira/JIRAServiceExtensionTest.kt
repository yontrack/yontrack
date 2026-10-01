package net.nemerosa.ontrack.extension.jira

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.jira.JIRAFixtures.jiraConfiguration
import net.nemerosa.ontrack.extension.jira.client.JIRAClient
import net.nemerosa.ontrack.extension.jira.model.JIRAIssue
import net.nemerosa.ontrack.extension.jira.model.JIRALink
import net.nemerosa.ontrack.extension.jira.model.JIRAStatus
import net.nemerosa.ontrack.extension.jira.tx.JIRASession
import net.nemerosa.ontrack.extension.jira.tx.JIRASessionFactory
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.support.MessageAnnotationUtils
import net.nemerosa.ontrack.tx.DefaultTransactionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JIRAServiceExtensionTest {

    private lateinit var jiraSessionFactory: JIRASessionFactory
    private lateinit var client: JIRAClient
    private lateinit var session: JIRASession
    private lateinit var service: JIRAServiceExtension

    @BeforeEach
    fun before() {
        jiraSessionFactory = mockk<JIRASessionFactory>()
        val transactionService = DefaultTransactionService()
        val jiraConfigurationService = mockk<JIRAConfigurationService>()

        client = mockk<JIRAClient>()

        session = mockk<JIRASession>()
        every { session.close() } just Runs
        every { session.client } returns client

        val propertyService = mockk<PropertyService>()

        service = JIRAServiceExtension(
            extensionFeature = JIRAExtensionFeature(),
            jiraConfigurationService = jiraConfigurationService,
            jiraSessionFactory = jiraSessionFactory,
            transactionService = transactionService
        )
    }

    @Test
    fun issueNotFound() {
        val config = jiraConfiguration()

        every { jiraSessionFactory.create(config) } returns session
        every { client.getIssue("XXX-1", config) } returns null

        val issue = service.getIssue(config, "XXX-1")
        assertNull(issue)
    }

    @Test
    fun getIssueTypes() {
        val issue = createIssue(1)
        val types = service.getIssueTypes(jiraConfiguration(), issue)
        assertEquals(
            setOf("Defect"),
            types
        )
    }

    @Test
    fun getMessageAnnotator() {
        val config = jiraConfiguration()
        val annotator = service.getMessageAnnotator(config)
        assertNotNull(annotator) {
            val message = MessageAnnotationUtils.annotate(
                "TEST-12, PRJ-12, PRJ-13 List of issues",
                listOf(it)
            )
            assertEquals(
                "<a href=\"http://jira/browse/TEST-12\">TEST-12</a>, <a href=\"http://jira/browse/PRJ-12\">PRJ-12</a>, <a href=\"http://jira/browse/PRJ-13\">PRJ-13</a> List of issues",
                message
            )
        }
    }

    @Test
    fun extractIssueKeysFromMessage() {
        val config = jiraConfiguration()
        val issues = service.extractIssueKeysFromMessage(
            config,
            "TEST-12, PRJ-12, PRJ-13 List of issues"
        )
        assertEquals(
            setOf("TEST-12", "PRJ-12", "PRJ-13"),
            issues
        )
    }

    @Test
    fun `Extract keys separated by a comma only`() {
        val issues = service.extractIssueKeysFromMessage(
            jiraConfiguration(),
            "ABC-1,ABC-2 List of issues"
        )
        assertEquals(
            setOf("ABC-1", "ABC-2"),
            issues
        )
    }

    @Test
    fun `Extract key from message with exclusion list`() {
        val config = jiraConfiguration(
            exclude = listOf("TEST")
        )
        val issues = service.extractIssueKeysFromMessage(
            config,
            "TEST-12, PRJ-12, PRJ-13 List of issues"
        )
        assertEquals(
            setOf("PRJ-12", "PRJ-13"),
            issues
        )
    }

    @Test
    fun `Extract key from message with inclusion list`() {
        val config = jiraConfiguration(
            include = listOf("PRJ")
        )
        val issues = service.extractIssueKeysFromMessage(
            config,
            "TEST-12, PRJ-12, PRJ-13 List of issues"
        )
        assertEquals(
            setOf("PRJ-12", "PRJ-13"),
            issues
        )
    }

    @Test
    fun `Extract key from message with inclusion and exclusion list`() {
        val config = jiraConfiguration(
            include = listOf("PRJ.*"),
            exclude = listOf("PRJX")
        )
        val issues = service.extractIssueKeysFromMessage(
            config,
            "TEST-12, PRJ-12, PRJ-13, PRJX-14 List of issues"
        )
        assertEquals(
            setOf("PRJ-12", "PRJ-13"),
            issues
        )
    }

    @Test
    fun `Following links`() {
        // Configuration to test with
        val config = jiraConfiguration()
        every { jiraSessionFactory.create(config) } returns session
        // Creating issues
        var issue1 = createIssue(1)
        var issue2 = createIssue(2)
        var issue3 = createIssue(3)
        var issue4 = createIssue(4)
        // Linking issues together
        issue1 = issue1.withLinks(
            listOf(
                createLink(2, "Depends", "depends on"),
                createLink(3, "Depends", "depends on")
            )
        )
        issue2 = issue2.withLinks(
            listOf(
                createLink(1, "Depends", "is depended on by"),
                createLink(4, "Depends", "depends on")
            )
        )
        issue3 = issue3.withLinks(
            listOf(
                createLink(1, "Depends", "is depended on by")
            )
        )
        issue4 = issue4.withLinks(
            listOf(
                createLink(2, "Depends", "is depended on by")
            )
        )

        // Client
        every { client.getIssue("TEST-1", config) } returns issue1
        every { client.getIssue("TEST-2", config) } returns issue2
        every { client.getIssue("TEST-3", config) } returns issue3
        every { client.getIssue("TEST-4", config) } returns issue4

        // Links from 1
        var issues = mutableMapOf<String, JIRAIssue>()
        service.followLinks(config, issue1, setOf("Depends"), issues)
        assertEquals(
            setOf(
                "TEST-1", "TEST-2", "TEST-3", "TEST-4"
            ),
            issues.values.map { it.key }.toSet()
        )
        // Links from 4
        issues = mutableMapOf()
        service.followLinks(config, issue4, setOf("Depends"), issues)
        assertEquals(
            setOf(
                "TEST-1", "TEST-2", "TEST-3", "TEST-4"
            ),
            issues.values.map { it.key }.toSet()
        )
    }

    private fun createLink(i: Int, name: String, relation: String) =
        JIRALink(
            "TEST-$i",
            "...",
            JIRAStatus("Open", "..."),
            name,
            relation
        )

    private fun createIssue(i: Int) =
        JIRAIssue(
            "1",
            "http://host/browser/TEST-$i",
            "TEST-$i",
            "Issue $i",
            JIRAStatus("Open", "..."),
            "",
            Time.now(),
            emptyList(),
            emptyList(),
            emptyList(),
            "Defect",
            emptyList()
        )

}
