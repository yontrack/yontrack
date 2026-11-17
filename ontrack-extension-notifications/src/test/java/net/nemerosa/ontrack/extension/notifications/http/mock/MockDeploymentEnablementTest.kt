package net.nemerosa.ontrack.extension.notifications.http.mock

import net.nemerosa.ontrack.common.RunProfile
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mock deployment API is switched on by a property rather than by the `dev` profile, so that a
 * demonstration instance - which does not run that profile - can have it. The `dev` profile sets the
 * property in `application-dev.yml`.
 */
class MockDeploymentEnablementTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(MockDeploymentController::class.java, GQLRootQueryHttpMockDeploymentsUrl::class.java)

    @Test
    fun `off by default`() {
        runner.run { context ->
            assertFalse(context.containsBean("mockDeploymentController"))
            assertTrue(context.getBeansOfType(GQLRootQueryHttpMockDeploymentsUrl::class.java).isEmpty())
        }
    }

    @Test
    fun `the query telling where it is comes with it`() {
        runner
            .withPropertyValues("ontrack.config.extension.notifications.http.mock.enabled=true")
            .run { context ->
                assertTrue(context.getBeansOfType(GQLRootQueryHttpMockDeploymentsUrl::class.java).isNotEmpty())
            }
    }

    @Test
    fun `off in the dev profile without the property`() {
        runner
            .withPropertyValues("spring.profiles.active=${RunProfile.DEV}")
            .run { context ->
                assertFalse(context.containsBean("mockDeploymentController"))
            }
    }

    @Test
    fun `off when the property is false`() {
        runner
            .withPropertyValues("ontrack.config.extension.notifications.http.mock.enabled=false")
            .run { context ->
                assertFalse(context.containsBean("mockDeploymentController"))
            }
    }

    @Test
    fun `on with the property`() {
        runner
            .withPropertyValues("ontrack.config.extension.notifications.http.mock.enabled=true")
            .run { context ->
                assertTrue(context.containsBean("mockDeploymentController"))
            }
    }
}
