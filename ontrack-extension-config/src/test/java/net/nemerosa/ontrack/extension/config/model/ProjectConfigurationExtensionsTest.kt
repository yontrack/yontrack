package net.nemerosa.ontrack.extension.config.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectConfigurationExtensionsTest {

    private val empty = ProjectConfiguration()

    private val configured = ProjectConfiguration(
        issueServiceIdentifier = ProjectIssueServiceIdentifier("jira", "config"),
    )

    @Test
    fun `No issue service identifier`() {
        assertNull(empty.getActualIssueServiceIdentifier(emptyMap()))
        assertFalse(empty.usesLegacyIssueServiceEnv(emptyMap()))
    }

    @Test
    fun `Issue service identifier from the configuration`() {
        val env = mapOf("ONTRACK_SCM_ISSUES" to "jira//legacy")
        assertEquals(
            ProjectIssueServiceIdentifier("jira", "config"),
            configured.getActualIssueServiceIdentifier(env)
        )
        assertFalse(configured.usesLegacyIssueServiceEnv(env))
    }

    @Test
    fun `Issue service identifier from the environment`() {
        val env = mapOf("YONTRACK_CI_SCM_ISSUES" to "jira//env")
        assertEquals(
            ProjectIssueServiceIdentifier("jira", "env"),
            empty.getActualIssueServiceIdentifier(env)
        )
        assertFalse(empty.usesLegacyIssueServiceEnv(env))
    }

    @Test
    fun `The environment wins over the legacy environment`() {
        val env = mapOf(
            "YONTRACK_CI_SCM_ISSUES" to "jira//env",
            "ONTRACK_SCM_ISSUES" to "jira//legacy",
        )
        assertEquals(
            ProjectIssueServiceIdentifier("jira", "env"),
            empty.getActualIssueServiceIdentifier(env)
        )
        assertFalse(empty.usesLegacyIssueServiceEnv(env))
    }

    @Test
    fun `Issue service identifier from the legacy environment`() {
        val env = mapOf("ONTRACK_SCM_ISSUES" to "jira//legacy")
        assertEquals(
            ProjectIssueServiceIdentifier("jira", "legacy"),
            empty.getActualIssueServiceIdentifier(env)
        )
        assertTrue(empty.usesLegacyIssueServiceEnv(env))
    }

    @Test
    fun `Blank legacy environment is not used`() {
        assertFalse(empty.usesLegacyIssueServiceEnv(mapOf("ONTRACK_SCM_ISSUES" to " ")))
    }
}
