package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobStatus
import net.nemerosa.ontrack.job.Schedule
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DefaultJobListenerIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun onJobError() {
        val jobListener = DefaultJobListener(meterRegistry)
        val jobStatus = JobStatus(
            id = 1,
            key = JobCategory.of("test").getType("test").getKey("1"),
            schedule = Schedule.EVERY_MINUTE,
            actualSchedule = Schedule.EVERY_MINUTE,
            description = "Test job",
            isDisabled = false,
            isPaused = false,
            isRunning = false,
            isValid = true,
            lastError = null,
            lastErrorCount = 0,
            lastTimeoutCount = 0,
            lastRunDate = null,
            lastRunDurationMs = 0,
            nextRunDate = null,
            progress = null,
            runCount = 0
        )
        jobListener.onJobError(jobStatus, RuntimeException("Test exception"))
    }
}