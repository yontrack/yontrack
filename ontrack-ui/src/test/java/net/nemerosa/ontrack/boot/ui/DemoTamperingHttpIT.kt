package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * The demonstration tampering switched off — the default: its endpoint does not exist, and answers
 * 404 even to a global administrator, not 403.
 *
 * Same context as `EvidenceHttpIT`. Not transactional: the server answers on its own threads.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemoTamperingHttpIT : DemoTamperingHttpSupport() {

    @Test
    fun `No tampering endpoint when the switch is off`() {
        val build = trailedBuild()
        val response = rewritePayload(build, 2, """{"status":"FORGED"}""", adminToken())
        assertEquals(404, response.statusCode(), response.body())
    }
}
