package net.nemerosa.ontrack.boot.ui

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminControllerTest {

    private val encryptionService = mockk<EncryptionService>()

    private val controller = AdminController(
        jobScheduler = mockk<JobScheduler>(),
        securityService = mockk<SecurityService>(),
        encryptionService = encryptionService,
    )

    @Test
    fun `importing the encryption key accepts it without echoing it back`() {
        every { encryptionService.importKey(any()) } just Runs

        val response = controller.importEncryptionKey("<script>alert(1)</script>")

        verify { encryptionService.importKey("<script>alert(1)</script>") }
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNull(response.body)
    }
}
