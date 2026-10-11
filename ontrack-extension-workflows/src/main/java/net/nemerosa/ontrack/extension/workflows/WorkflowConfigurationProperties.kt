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

    @APIDescription("Recovery of the workflow nodes left unfinished by a stopped Yontrack process")
    var recovery = RecoveryConfigProperties()

    /**
     * Recovery of the workflow nodes left unfinished by a stopped Yontrack process.
     *
     * A process marks the nodes it runs as alive through a heartbeat. The nodes of a process
     * without a recent heartbeat are recovered: resumed if they had not started executing, in
     * error otherwise.
     */
    class RecoveryConfigProperties {
        @APIDescription("Interval between two heartbeats of a Yontrack process, which mark the workflow nodes it runs as alive")
        @DurationUnit(ChronoUnit.SECONDS)
        var heartbeat: Duration = Duration.ofSeconds(30)

        @APIDescription(
            "Time after its last heartbeat beyond which a Yontrack process is considered stopped, " +
                    "and the workflow nodes it was running are recovered. Must be well above the heartbeat interval."
        )
        @DurationUnit(ChronoUnit.SECONDS)
        var staleAfter: Duration = Duration.ofMinutes(3)

        @APIDescription(
            "Time after its last dispatch beyond which a workflow node which no process has picked up " +
                    "is dispatched again"
        )
        @DurationUnit(ChronoUnit.SECONDS)
        var createdStaleAfter: Duration = Duration.ofMinutes(10)

        @APIDescription(
            "Age of a workflow instance beyond which its abandoned nodes are put in error instead of being resumed. " +
                    "Also how long a stopped Yontrack process is remembered."
        )
        @DurationUnit(ChronoUnit.SECONDS)
        var maxAge: Duration = Duration.ofDays(1)

        @APIDescription("Interval between two runs of the recovery of the workflow nodes")
        @DurationUnit(ChronoUnit.SECONDS)
        var interval: Duration = Duration.ofMinutes(1)
    }

    /**
     * Nested rather than a flat `mockExecutorEnabled`, so that the generated documentation
     * publishes a usable environment variable: the doc generator uppercases a camel-case field
     * without splitting it, and only the dots of a nested property become underscores.
     */
    class MockExecutorConfigProperties {
        @APIDescription(
            "Enables the \"Simulated gate\" workflow node executor, which reports a pre-configured outcome " +
                    "without performing any real action. Disabled by default; always enabled in the `dev` " +
                    "profile. Enable it only on " +
                    "demonstration or test instances - never on an instance tracking real deliveries, where it " +
                    "would let a workflow report a gate as passed without anything having been verified."
        )
        var enabled: Boolean = false
    }

}
