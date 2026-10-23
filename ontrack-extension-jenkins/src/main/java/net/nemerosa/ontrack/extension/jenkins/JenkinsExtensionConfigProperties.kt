package net.nemerosa.ontrack.extension.jenkins

import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "ontrack.config.extension.jenkins")
@Component
class JenkinsExtensionConfigProperties {

    @APIDescription("Mock Jenkins properties")
    var mock = MockJenkinsConfigProperties()

    /**
     * Nested rather than a flat `mockEnabled`, so that the generated documentation publishes a
     * usable environment variable: the doc generator uppercases a camel-case field without
     * splitting it, and only the dots of a nested property become underscores.
     */
    class MockJenkinsConfigProperties {
        @APIDescription(
            "Enables the `mock-jenkins` notification channel, which runs Jenkins jobs in memory - " +
                    "succeeding, or failing when the job is given `result: FAILURE` - instead of calling " +
                    "a real Jenkins. Disabled by default; always enabled in the `dev` profile. Enable it " +
                    "only on demonstration or test instances - never on an instance tracking real " +
                    "deliveries, where it would let a workflow report a pipeline as passed without " +
                    "anything having run. Its jobs live in the memory of each backend, and neither " +
                    "survive a restart nor are shared between replicas."
        )
        var enabled: Boolean = false
    }

    companion object {
        /** Property switching the mock Jenkins on, for `@ConditionalOnProperty`. */
        const val MOCK_ENABLED = "ontrack.config.extension.jenkins.mock.enabled"
    }

}
