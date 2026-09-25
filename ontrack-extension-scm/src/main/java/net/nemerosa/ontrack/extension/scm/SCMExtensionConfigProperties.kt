package net.nemerosa.ontrack.extension.scm

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.job.Schedule
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties(prefix = "ontrack.config.extension.scm")
@Component
class SCMExtensionConfigProperties {

    @APIDescription("SCM catalog properties")
    var catalog = SCMCatalogConfigProperties()

    @APIDescription("SCM search properties")
    var search = SCMSearchConfigProperties()

    @APIDescription("Mock SCM properties")
    var mock = MockSCMConfigProperties()

    /**
     * Nested rather than a flat `mockEnabled`, so that the generated documentation publishes a
     * usable environment variable: the doc generator uppercases a camel-case field without
     * splitting it, and only the dots of a nested property become underscores.
     */
    class MockSCMConfigProperties {
        @APIDescription(
            "Enables the mock SCM, which keeps repositories, branches, commits and issues in memory " +
                    "instead of talking to a real SCM. Disabled by default; always enabled in the `dev` " +
                    "profile (the value shown opposite is the one the documentation build runs with). " +
                    "Enable it only on demonstration or test instances - never on an instance tracking " +
                    "real deliveries, where it would let a project claim an SCM that answers with " +
                    "whatever anyone posted to it. Its data lives on the bean and does not survive a " +
                    "restart unless `persistent` is set."
        )
        var enabled: Boolean = false

        @APIDescription(
            "Keeps the mock SCM's repositories in the database, so that its commits, issues, files " +
                    "and pull requests survive a restart. Disabled by default: tests create their mock " +
                    "data inside the test and would only pay for the writes. Set it on a long-lived " +
                    "instance configured with the mock SCM - a demonstration instance, or a development " +
                    "stack whose backend is restarted often - where losing the data means the change log " +
                    "of an already-seeded project fails until the next reset. What it writes is an " +
                    "implementation detail of the mock SCM and not a storage format to depend on. Has " +
                    "no effect unless `enabled` is set."
        )
        var persistent: Boolean = false
    }

    class SCMCatalogConfigProperties {
        @APIDescription("Enabling the SCM catalog")
        var enabled = false
    }

    class SCMSearchConfigProperties {
        @DurationUnit(ChronoUnit.HOURS)
        @APIDescription(
            "Interval between two incremental indexations of the SCM commits for the search, in hours. " +
                    "Each one scans only the commits after the last indexed one of each project; a full " +
                    "indexation of all the commits runs weekly."
        )
        var schedule: Duration = Duration.ofHours(1)

        @APIDescription(
            "Enabling the scheduled indexation of the SCM commits for the search, incremental and weekly. " +
                    "When disabled, the commits are indexed only on demand."
        )
        var scheduled: Boolean = true

        /**
         * Converting the [schedule] property to a job [Schedule]
         */
        fun toSchedule(): Schedule =
            if (scheduled) {
                Schedule.everyMinutes(schedule.toMinutes())
            } else {
                Schedule.NONE
            }
    }

}
