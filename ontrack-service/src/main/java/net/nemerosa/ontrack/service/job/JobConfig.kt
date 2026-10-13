package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.job.JobListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The scheduler of the jobs is [QuartzJobScheduler].
 */
@Configuration
class JobConfig(
    private val meterRegistry: MeterRegistry,
) {
    @Bean
    fun jobListener(): JobListener = DefaultJobListener(
        meterRegistry = meterRegistry,
    )
}
