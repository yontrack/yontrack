package net.nemerosa.ontrack.extension.gitlab.notifications

import net.nemerosa.ontrack.extension.gitlab.AbstractGitLabTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wiring of the `gitlab-pipeline` channel in a full context: that it is registered at all, and that the
 * configuration it is given is checked against the GitLab configurations which actually exist.
 *
 * Nothing here talks to GitLab: validating a subscription only reads the Yontrack configuration, and the
 * channel is never published.
 */
class GitLabPipelineNotificationChannelIT : AbstractGitLabTestSupport() {

    @Autowired
    private lateinit var notificationChannelRegistry: NotificationChannelRegistry

    @Autowired
    private lateinit var channel: GitLabPipelineNotificationChannel

    private fun validate(
        config: String,
        project: String = "group/subgroup/project",
        ref: String = "main",
        variables: List<GitLabPipelineNotificationChannelConfigVariable> = emptyList(),
    ) = channel.validate(
        GitLabPipelineNotificationChannelConfig(
            config = config,
            project = project,
            ref = ref,
            variables = variables,
        ).asJson()
    )

    @Test
    fun `The channel is registered`() {
        val registered = notificationChannelRegistry.findChannel("gitlab-pipeline")
        assertNotNull(registered, "The gitlab-pipeline channel is registered") {
            assertEquals("GitLab pipeline", it.displayName)
            assertTrue(it.enabled, "The channel is enabled")
        }
    }

    @Test
    fun `A subscription naming an existing configuration is valid`() {
        val configuration = gitLabConfig()
        val validated = validate(
            config = configuration.name,
            variables = listOf(GitLabPipelineNotificationChannelConfigVariable("VERSION", "\${build}")),
        )
        assertTrue(validated.isOk(), "Subscription is valid: ${validated.message}")
        assertEquals("group/subgroup/project", validated.config?.project)
    }

    @Test
    fun `A subscription naming an unknown configuration is rejected`() {
        val unknown = uid("gl_")
        val validated = validate(config = unknown)
        assertFalse(validated.isOk(), "Subscription is rejected")
        assertEquals("GitLab configuration $unknown could not be found", validated.message)
    }

    @Test
    fun `A subscription without a project is rejected`() {
        val configuration = gitLabConfig()
        val validated = validate(config = configuration.name, project = "")
        assertFalse(validated.isOk(), "Subscription is rejected")
        assertEquals("GitLab project is required", validated.message)
    }

    @Test
    fun `A subscription without a ref is rejected`() {
        val configuration = gitLabConfig()
        val validated = validate(config = configuration.name, ref = "")
        assertFalse(validated.isOk(), "Subscription is rejected")
        assertEquals("GitLab pipeline ref is required", validated.message)
    }

    @Test
    fun `A subscription with an unnamed variable is rejected`() {
        val configuration = gitLabConfig()
        val validated = validate(
            config = configuration.name,
            variables = listOf(GitLabPipelineNotificationChannelConfigVariable("", "1.0.0")),
        )
        assertFalse(validated.isOk(), "Subscription is rejected")
        assertEquals("GitLab pipeline variable names are required", validated.message)
    }
}
