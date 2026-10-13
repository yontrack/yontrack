package net.nemerosa.ontrack.boot.ui

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobLaunch
import net.nemerosa.ontrack.job.JobLaunchStatus
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminControllerTest {

    private val encryptionService = mockk<EncryptionService>()

    private val jobScheduler = mockk<JobScheduler>()

    private val controller = AdminController(
        jobScheduler = jobScheduler,
        securityService = mockk<SecurityService>(relaxed = true),
        encryptionService = encryptionService,
    )

    private val jobKey = JobCategory.of("test").getType("launch").getKey("job")

    @Test
    fun `launching a job which is already running answers so, with the replica running it`() {
        every { jobScheduler.getJobKey(1) } returns Optional.of(jobKey)
        every { jobScheduler.launch(jobKey) } returns JobLaunch(JobLaunchStatus.ALREADY_RUNNING, runningOn = "replica-a")

        val response = controller.launchJob(1)

        assertEquals(JobLaunchResponse(success = false, status = JobLaunchStatus.ALREADY_RUNNING, runningOn = "replica-a"), response)
    }

    @Test
    fun `launching a job which is not running triggers it`() {
        every { jobScheduler.getJobKey(1) } returns Optional.of(jobKey)
        every { jobScheduler.launch(jobKey) } returns JobLaunch(JobLaunchStatus.TRIGGERED, future = CompletableFuture<Any>())

        val response = controller.launchJob(1)

        assertEquals(JobLaunchResponse(success = true, status = JobLaunchStatus.TRIGGERED, runningOn = null), response)
    }

    @Test
    fun `launching an unknown job fails`() {
        every { jobScheduler.getJobKey(1) } returns Optional.empty()

        val response = controller.launchJob(1)

        assertEquals(JobLaunchResponse(success = false, status = null, runningOn = null), response)
    }

    @Test
    fun `importing the encryption key accepts it without echoing it back`() {
        every { encryptionService.importKey(any()) } just Runs

        val response = controller.importEncryptionKey("<script>alert(1)</script>")

        verify { encryptionService.importKey("<script>alert(1)</script>") }
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNull(response.body)
    }
}
