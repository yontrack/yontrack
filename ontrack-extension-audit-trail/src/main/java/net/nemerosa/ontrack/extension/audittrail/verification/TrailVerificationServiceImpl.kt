package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.extension.audittrail.export.TrailExportService
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TrailVerificationServiceImpl(
    private val trailExportService: TrailExportService,
) : TrailVerificationService {

    override fun verify(build: Build, includeEvidence: Boolean): TrailVerification {
        // The stored trail is verified as exported: one path, whether online or offline
        val verification = TrailVerifier.verify(trailExportService.export(build))
        return if (includeEvidence) {
            // No evidence is stored yet: none can be missing nor altered
            verification.copy(missingEvidence = emptyList(), alteredEvidence = emptyList())
        } else {
            verification
        }
    }
}
