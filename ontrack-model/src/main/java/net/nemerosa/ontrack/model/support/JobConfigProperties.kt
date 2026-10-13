package net.nemerosa.ontrack.model.support

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

class JobConfigProperties {
    @Min(1)
    @APIDescription("Number of threads to use to run the background jobs, on each replica: the size of the thread pool of Quartz.")
    var poolSize = 10

    @APIDescription("Interval (in minutes) between each refresh of the job list: each replica reads again the jobs of the providers, and the jobs of the cluster are reconciled with them once per interval.")
    @Min(1)
    var orchestration = 2

    /**
     * Pausing all the jobs at startup. Pausing all the jobs being stored and applying to every
     * replica (#2077), one replica starting with this property pauses the cluster.
     */
    @APIDescription("Deprecated. Set to true to pause all the jobs when this replica starts: as the pause of all the jobs is stored and applies to every replica, this pauses the whole cluster until an administrator resumes all the jobs. Use the \"Pause all\" action instead.")
    @Deprecated("Removed in V7. Use the \"Pause all\" action instead. See #2077")
    var pausedAtStartup = false

    @APIDescription("Enabling the scattering of jobs. When several jobs have the same schedule, this can create a peak of activity, potentially harmful for the performances of the application. Enabling scattering allows jobs to be scheduled with an additional delay, computed as a fraction of the period.")
    var scattering = true

    @APIDescription("Scattering ratio. Maximum fraction of the period to take into account for the scattering. For example, setting 0.5 would not add a period greater than half the period of the job. Setting 0 would actually disable the scattering altogether.")
    @Min(1)
    @Max(1)
    var scatteringRatio = 1.0

    @APIDescription("# Global timeout for all jobs. Any job running longer than this time will be forcibly stopped (expressed by default in hours)")
    @DurationUnit(ChronoUnit.HOURS)
    var timeout: Duration = Duration.ofHours(4)

    @APIDescription("Amount of time to wait between two controls of the job timeouts (expressed by default in minutes)")
    @DurationUnit(ChronoUnit.MINUTES)
    var timeoutControllerInterval: Duration = Duration.ofMinutes(15)

    @APIDescription("Probe jobs, for the acceptance tests of the jobs. Never to be enabled in production.")
    var testing = TestingProperties()

    /**
     * Probe jobs, used by the acceptance tests of the jobs across several replicas (#2077): a
     * periodic one and a manual one, which do nothing but being run.
     */
    class TestingProperties {
        @APIDescription("Registers the probe jobs. Never to be enabled in production.")
        var enabled = false

        @APIDescription("Period of the periodic probe job")
        var period: Duration = Duration.ofSeconds(2)
    }

}