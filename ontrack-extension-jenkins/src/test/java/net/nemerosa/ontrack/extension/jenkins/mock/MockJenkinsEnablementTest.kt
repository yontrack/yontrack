package net.nemerosa.ontrack.extension.jenkins.mock

import io.mockk.mockk
import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClientFactory
import net.nemerosa.ontrack.model.events.EventTemplatingService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mock Jenkins is switched on by a property rather than by the `dev` profile, so that a
 * demonstration instance - which does not run that profile - can have it, and only it.
 */
class MockJenkinsEnablementTest {

    private val runner = ApplicationContextRunner()
        .withBean(JenkinsConfigurationService::class.java, { mockk<JenkinsConfigurationService>() })
        .withBean(EventTemplatingService::class.java, { mockk<EventTemplatingService>() })
        .withUserConfiguration(
            MockJenkinsClients::class.java,
            MockJenkinsClientFactory::class.java,
            MockJenkinsNotificationChannel::class.java,
            MockJenkinsController::class.java,
        )

    @Test
    fun `off by default`() {
        runner.run { context ->
            assertFalse(context.containsBean("mockJenkinsNotificationChannel"))
            assertFalse(context.containsBean("mockJenkinsController"))
        }
    }

    @Test
    fun `on with the property, without the dev profile and without replacing the real Jenkins clients`() {
        runner
            .withPropertyValues("ontrack.config.extension.jenkins.mock.enabled=true")
            .run { context ->
                assertTrue(context.containsBean("mockJenkinsNotificationChannel"))
                assertTrue(context.containsBean("mockJenkinsController"))
                assertTrue(
                    context.getBeansOfType(JenkinsClientFactory::class.java).isEmpty(),
                    "Outside of the dev profile, the Jenkins clients stay the real ones",
                )
            }
    }

    @Test
    fun `the dev profile still mocks every Jenkins client`() {
        runner
            .withPropertyValues("spring.profiles.active=${RunProfile.DEV}")
            .run { context ->
                assertTrue(context.getBean(JenkinsClientFactory::class.java) is MockJenkinsClientFactory)
            }
    }
}
