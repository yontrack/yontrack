package net.nemerosa.ontrack.extension.audittrail.message

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.AuditTrailExtensionFeature
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyStatus
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageStatus
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.model.message.Message
import net.nemerosa.ontrack.model.message.MessageType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuditTrailMessageTest {

    private val licenseControlService = mockk<LicenseControlService>()
    private val evidenceStorageService = mockk<EvidenceStorageService>()
    private val instanceKeyService = mockk<InstanceKeyService>()
    private val auditTrailConfigProperties = AuditTrailConfigProperties()

    private val message = AuditTrailMessage(
        extensionFeature = mockk<AuditTrailExtensionFeature>(relaxed = true),
        auditTrailConfigProperties = auditTrailConfigProperties,
        auditTrailLicense = AuditTrailLicense(licenseControlService),
        evidenceStorageService = evidenceStorageService,
        instanceKeyService = instanceKeyService,
    )

    private val tamperingMessage = Message(
        type = MessageType.ERROR,
        content = "This instance allows trail tampering for demonstration: its trails prove nothing.",
    )

    private fun given(
        licensed: Boolean = true,
        storage: EvidenceStorageState = EvidenceStorageState.OK,
        key: InstanceKeyStatus = InstanceKeyStatus.OK,
        demoTampering: Boolean = false,
    ) {
        auditTrailConfigProperties.demoTampering.enabled = demoTampering
        every { licenseControlService.isFeatureEnabled(FEATURE_AUDIT_TRAIL) } returns licensed
        every { evidenceStorageService.status } returns EvidenceStorageStatus(
            state = storage,
            message = if (storage == EvidenceStorageState.OK) null else "Some reason",
            checkedAt = Time.now(),
        )
        every { instanceKeyService.keyStatus } returns key
    }

    @Test
    fun `No message when the storage is OK and the key provisioned`() {
        given()
        assertEquals(emptyList(), message.globalMessages)
    }

    @Test
    fun `Warning when no storage is configured`() {
        given(storage = EvidenceStorageState.NOT_CONFIGURED)
        assertEquals(
            listOf(
                Message(
                    type = MessageType.WARNING,
                    content = "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached.",
                )
            ),
            message.globalMessages
        )
    }

    @Test
    fun `Error when the storage is unreachable, without telling everybody why`() {
        given(storage = EvidenceStorageState.UNREACHABLE)
        assertEquals(
            listOf(
                Message(
                    type = MessageType.ERROR,
                    content = "Audit trail is enabled but its evidence storage is unreachable: evidence cannot be attached.",
                )
            ),
            message.globalMessages
        )
    }

    @Test
    fun `Error when the instance key is not provisioned`() {
        given(key = InstanceKeyStatus.NOT_PROVISIONED)
        assertEquals(
            listOf(
                Message(
                    type = MessageType.ERROR,
                    content = "Audit trail is enabled but its instance key is not provisioned: entries are not endorsed.",
                )
            ),
            message.globalMessages
        )
    }

    @Test
    fun `Storage and key messages together`() {
        given(storage = EvidenceStorageState.NOT_CONFIGURED, key = InstanceKeyStatus.NOT_PROVISIONED)
        assertEquals(
            listOf(MessageType.WARNING, MessageType.ERROR),
            message.globalMessages.map { it.type }
        )
    }

    @Test
    fun `No message while the licence is off, whatever the storage and the key`() {
        given(
            licensed = false,
            storage = EvidenceStorageState.UNREACHABLE,
            key = InstanceKeyStatus.NOT_PROVISIONED,
        )
        assertEquals(emptyList(), message.globalMessages)
    }

    @Test
    fun `Demo tampering is off by default`() {
        assertEquals(false, AuditTrailConfigProperties().demoTampering.enabled)
    }

    @Test
    fun `Permanent error while demo tampering is allowed`() {
        given(demoTampering = true)
        assertEquals(listOf(tamperingMessage), message.globalMessages)
    }

    @Test
    fun `Demo tampering error first, before the storage and key messages`() {
        given(
            demoTampering = true,
            storage = EvidenceStorageState.NOT_CONFIGURED,
            key = InstanceKeyStatus.NOT_PROVISIONED,
        )
        val messages = message.globalMessages
        assertEquals(tamperingMessage, messages.first())
        assertEquals(listOf(MessageType.ERROR, MessageType.WARNING, MessageType.ERROR), messages.map { it.type })
    }

    @Test
    fun `Demo tampering error even while the licence is off`() {
        given(licensed = false, demoTampering = true)
        assertEquals(listOf(tamperingMessage), message.globalMessages)
    }
}
