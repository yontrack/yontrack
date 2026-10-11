package net.nemerosa.ontrack.model.support

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

class JobConfigProperties {
    @Min(1)
    @APIDescription("Number of threads to use to run the background jobs. Also sizes the pool of the database connections holding the run locks of the jobs, which is separate from the main pool: at most this number, plus a margin for the locks taken outside the jobs.")
    var poolSize = 10

    @APIDescription("Interval (in minutes) between each refresh of the job list")
    @Min(1)
    var orchestration = 2

    @APIDescription("Set to true to not start any job at application startup. The administrator can restore the scheduling jobs manually")
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

}