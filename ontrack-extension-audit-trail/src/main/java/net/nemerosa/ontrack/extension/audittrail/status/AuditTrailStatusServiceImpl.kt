package net.nemerosa.ontrack.extension.audittrail.status

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Service

@Service
class AuditTrailStatusServiceImpl(
    private val securityService: SecurityService,
    private val auditTrailLicense: AuditTrailLicense,
    private val auditTrailConfigProperties: AuditTrailConfigProperties,
    private val evidenceStorageService: EvidenceStorageService,
    private val instanceKeyService: InstanceKeyService,
) : AuditTrailStatusService {

    override val status: AuditTrailStatus
        get() {
            securityService.checkGlobalFunction(GlobalSettings::class.java)
            val storage = auditTrailConfigProperties.storage
            val storageStatus = evidenceStorageService.status
            return AuditTrailStatus(
                licensed = auditTrailLicense.auditTrailEnabled,
                storage = AuditTrailStorageStatus(
                    state = storageStatus.state,
                    message = storageStatus.message,
                    checkedAt = storageStatus.checkedAt,
                    endpoint = storage.endpoint,
                    bucket = storage.bucket,
                    region = storage.region,
                    pathStyle = storage.pathStyle,
                ),
                keyStatus = instanceKeyService.keyStatus,
                keys = instanceKeyService.getPublicKeys(),
            )
        }
}
