package net.nemerosa.ontrack.extension.workflows

import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

@Component
@ConfigurationProperties(prefix = "ontrack.config.extension.workflows")
class WorkflowConfigurationProperties {

    @APIDescription("Time to wait for the completion of the parents of a node in a workflow")
    @DurationUnit(ChronoUnit.SECONDS)
    var parentWaitingInterval: Duration = Duration.ofSeconds(1)

    @APIDescription("Default time to wait between checks of an async process inside the execution of a node")
    @DurationUnit(ChronoUnit.SECONDS)
    var asyncCheckInterval: Duration = Duration.ofSeconds(1)

    @APIDescription("\"Simulated gate\" node executor properties")
    var mock = MockExecutorConfigProperties()

    /**
     * Nested rather than a flat `mockExecutorEnabled`, so that the generated documentation
     * publishes a usable environment variable: the doc generator uppercases a camel-case field
     * without splitting it, and only the dots of a nested property become underscores.
     */
    class MockExecutorConfigProperties {
        @APIDescription(
            "Enables the \"Simulated gate\" workflow node executor, which reports a pre-configured outcome " +
                    "without performing any real action. Disabled by default; always enabled in the `dev` " +
                    "profile (the value shown opposite is the one the documentation build runs with). Enable it only on " +
                    "demonstration or test instances - never on an instance tracking real deliveries, where it " +
                    "would let a workflow report a gate as passed without anything having been verified."
        )
        var enabled: Boolean = false
    }

}
