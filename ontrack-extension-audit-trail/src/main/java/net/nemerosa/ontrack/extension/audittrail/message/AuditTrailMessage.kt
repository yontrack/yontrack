package net.nemerosa.ontrack.extension.audittrail.message

import net.nemerosa.ontrack.extension.api.GlobalMessageExtension
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.AuditTrailExtensionFeature
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyStatus
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.message.Message
import net.nemerosa.ontrack.model.message.MessageType
import org.springframework.stereotype.Component

/**
 * Global messages of the audit trail, shown to everybody:
 *
 * - while the demonstration tampering is allowed, a permanent error, whatever the licence: the
 *   trails already written can be tampered with and are still shown and exported after the licence
 *   lapses;
 * - only while the licence is on, the evidence storage which is not configured or unreachable, and
 *   the instance key which is not provisioned.
 *
 * The messages do not say why: the reason, which may name hosts and buckets, is on the audit
 * trail status page, for the administrators.
 */
@Component
class AuditTrailMessage(
    extensionFeature: AuditTrailExtensionFeature,
    private val auditTrailConfigProperties: AuditTrailConfigProperties,
    private val auditTrailLicense: AuditTrailLicense,
    private val evidenceStorageService: EvidenceStorageService,
    private val instanceKeyService: InstanceKeyService,
) : AbstractExtension(extensionFeature), GlobalMessageExtension {

    override val globalMessages: List<Message>
        get() = listOfNotNull(demoTamperingMessage()) +
                if (auditTrailLicense.auditTrailEnabled) {
                    listOfNotNull(storageMessage(), keyMessage())
                } else {
                    emptyList()
                }

    private fun demoTamperingMessage(): Message? =
        if (auditTrailConfigProperties.demoTampering.enabled) {
            Message(
                type = MessageType.ERROR,
                content = "This instance allows trail tampering for demonstration: its trails prove nothing.",
            )
        } else {
            null
        }

    private fun storageMessage(): Message? =
        when (evidenceStorageService.status.state) {
            EvidenceStorageState.NOT_CONFIGURED -> Message(
                type = MessageType.WARNING,
                content = "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached.",
            )

            EvidenceStorageState.UNREACHABLE -> Message(
                type = MessageType.ERROR,
                content = "Audit trail is enabled but its evidence storage is unreachable: evidence cannot be attached.",
            )

            EvidenceStorageState.OK -> null
        }

    private fun keyMessage(): Message? =
        when (instanceKeyService.keyStatus) {
            InstanceKeyStatus.NOT_PROVISIONED -> Message(
                type = MessageType.ERROR,
                content = "Audit trail is enabled but its instance key is not provisioned: entries are not endorsed.",
            )

            InstanceKeyStatus.OK -> null
        }
}
