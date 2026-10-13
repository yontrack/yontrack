package net.nemerosa.ontrack.graphql.schema.jobs

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import kotlin.jvm.optionals.getOrNull

@Controller
class JobController(
    private val securityService: SecurityService,
    private val jobScheduler: JobScheduler,
) {

    @MutationMapping
    fun launchJob(@Argument id: Long): JobActionResult {
        return withJobKey(id) { key ->
            JobActionResult.launch(id, jobScheduler.launch(key))
        }
    }

    @MutationMapping
    fun pauseJob(@Argument id: Long): JobActionResult {
        return withJobKey(id) { key ->
            val ok = jobScheduler.pause(key)
            JobActionResult.check(ok, "Could not pause job with ID = $id")
        }
    }

    @MutationMapping
    fun resumeJob(@Argument id: Long): JobActionResult {
        return withJobKey(id) { key ->
            val ok = jobScheduler.resume(key)
            JobActionResult.check(ok, "Could not resume job with ID = $id")
        }
    }

    @MutationMapping
    fun stopJob(@Argument id: Long): JobActionResult {
        return withJobKey(id) { key ->
            val ok = jobScheduler.stop(key)
            JobActionResult.check(ok, "Could not stop job with ID = $id")
        }
    }

    @MutationMapping
    fun deleteJob(@Argument id: Long): JobActionResult {
        return withJobKey(id) { key ->
            val status = jobScheduler.getJobStatus(key).getOrNull()
            val ok = status != null && !status.isValid && jobScheduler.unschedule(key)
            JobActionResult.check(ok, "Could not delete job with ID = $id")
        }
    }

    @MutationMapping
    fun pauseAllJobs(): JobActionResult {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        jobScheduler.pause()
        return JobActionResult.ok()
    }

    @MutationMapping
    fun resumeAllJobs(): JobActionResult {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        jobScheduler.resume()
        return JobActionResult.ok()
    }

    private fun withJobKey(
        id: Long,
        code: (key: JobKey) -> JobActionResult,
    ): JobActionResult {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        val key = jobScheduler.getJobKey(id).getOrNull()
            ?: return JobActionResult.check(false, "Could not find job with ID = $id")
        return code(key)
    }

    @QueryMapping
    fun jobExecutionStatus(): JobExecutionStatus {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return JobExecutionStatus(
            paused = jobScheduler.isPaused,
        )
    }

}