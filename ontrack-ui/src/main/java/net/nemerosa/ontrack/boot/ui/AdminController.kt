package net.nemerosa.ontrack.boot.ui

import jakarta.validation.Valid
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.job.JobStatus
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.NameDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/rest/admin")
class AdminController
@Autowired
constructor(
    private val jobScheduler: JobScheduler,
    private val securityService: SecurityService,
    private val encryptionService: EncryptionService
) {

    /**
     * Gets the job filters
     */
    @GetMapping("jobs/filter")
    fun getJobFilter(): JobFilterResources {
        // All job types
        val types = jobScheduler.allJobKeys
            .map { it.type }
            .distinctBy { it.key }
        // All categories
        val categories = types
            .map { it.category }
            .distinctBy { it.key }
            .map {
                NameDescription(
                    it.key,
                    it.name
                )
            }
        // Indexation of types per category
        val indexedTypes = types.groupBy { it.category.key }
        // OK
        return JobFilterResources(
            categories,
            indexedTypes.mapValues { (_, typeList) ->
                typeList.map { NameDescription(it.key, it.name) }
            }
        )
    }

    /**
     * Gets the list of jobs and their status
     */
    @GetMapping("jobs")
    fun getJobs(
        @Valid jobFilter: JobFilter?,
    ): List<JobStatus> {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return (jobFilter ?: JobFilter()).filter(jobScheduler.jobStatuses)
    }

    /**
     * Launches a job
     */
    @PostMapping("jobs/{id:\\d+}")
    fun launchJob(@PathVariable id: Long): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return jobScheduler.getJobKey(id)
            .map { key -> Ack.validate(jobScheduler.fireImmediately(key) != null) }
            .orElse(Ack.NOK)
    }

    /**
     * Pauses all job executions
     */
    @PutMapping("jobs/pause")
    fun pauseAllJobs(): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        jobScheduler.pause()
        return Ack.OK
    }

    /**
     * Resumes all job executions
     */
    @PutMapping("jobs/resume")
    fun resumeAllJobs(): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        jobScheduler.resume()
        return Ack.OK
    }

    /**
     * Pauses a job
     */
    @PostMapping("jobs/{id:\\d+}/pause")
    fun pauseJob(@PathVariable id: Long): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return jobScheduler.getJobKey(id)
            .map { key -> Ack.validate(jobScheduler.pause(key)) }
            .orElse(Ack.NOK)
    }

    /**
     * Resumes a job
     */
    @PostMapping("jobs/{id:\\d+}/resume")
    fun resumeJob(@PathVariable id: Long): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return jobScheduler.getJobKey(id)
            .map { key -> Ack.validate(jobScheduler.resume(key)) }
            .orElse(Ack.NOK)
    }

    /**
     * Deleting a job
     */
    @DeleteMapping("jobs/{id:\\d+}")
    fun deleteJob(@PathVariable id: Long): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return jobScheduler.getJobKey(id)
            .filter { key -> !jobScheduler.getJobStatus(key).get().isValid }
            .map { key -> Ack.validate(jobScheduler.unschedule(key)) }
            .orElse(Ack.NOK)
    }

    /**
     * Stopping a job
     */
    @DeleteMapping("jobs/{id:\\d+}/stop")
    fun stopJob(@PathVariable id: Long): Ack {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        return jobScheduler.getJobKey(id)
            .map { key -> Ack.validate(jobScheduler.stop(key)) }
            .orElse(Ack.NOK)
    }

    /**
     * Exporting the encryption key
     */
    @GetMapping("/encryption")
    fun exportEncryptionKey(): ResponseEntity<String> =
        encryptionService.exportKey()
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    /**
     * Importing the encryption key. The key is not echoed back: the response carries no body.
     */
    @PutMapping("/encryption")
    fun importEncryptionKey(@RequestBody payload: String): ResponseEntity<Void> {
        encryptionService.importKey(payload)
        return ResponseEntity.accepted().build()
    }

}
