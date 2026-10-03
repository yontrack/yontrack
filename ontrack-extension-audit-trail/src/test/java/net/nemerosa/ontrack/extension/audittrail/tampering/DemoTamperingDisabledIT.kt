package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.ui.DemoTamperingController
import net.nemerosa.ontrack.model.message.GlobalMessageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The demonstration tampering, switched off — the default: neither the endpoint nor the service
 * behind it exist, and no message is shown. Over HTTP, the endpoint answers 404 (see
 * `DemoTamperingHttpIT` in `ontrack-ui`).
 */
class DemoTamperingDisabledIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var auditTrailConfigProperties: AuditTrailConfigProperties

    @Autowired
    private lateinit var globalMessageService: GlobalMessageService

    @Test
    fun `Off by default`() {
        assertEquals(false, auditTrailConfigProperties.demoTampering.enabled)
    }

    @Test
    fun `No tampering endpoint nor service when the switch is off`() {
        assertTrue(applicationContext.getBeanNamesForType(DemoTamperingController::class.java).isEmpty())
        assertTrue(applicationContext.getBeanNamesForType(DemoTamperingService::class.java).isEmpty())
    }

    @Test
    fun `No tampering message when the switch is off`() {
        assertTrue(
            globalMessageService.globalMessages.none { it.content.contains("trail tampering") },
            "No tampering message"
        )
    }
}
