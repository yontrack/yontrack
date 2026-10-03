package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceBlobCheck
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceBlobStore
import net.nemerosa.ontrack.extension.audittrail.export.TrailExport
import net.nemerosa.ontrack.extension.audittrail.export.TrailExportService
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TrailVerificationServiceImpl(
    private val trailExportService: TrailExportService,
    private val evidenceBlobStore: EvidenceBlobStore,
) : TrailVerificationService {

    override fun verify(build: Build, includeEvidence: Boolean): TrailVerification {
        // The stored trail is verified as exported: one path, whether online or offline
        val export = trailExportService.export(build)
        val verification = TrailVerifier.verify(export)
        return if (includeEvidence) {
            verifyEvidence(export, verification)
        } else {
            verification
        }
    }

    /**
     * Checks the blob of every `evidence.attached` entry against the SHA-256 the entry recorded —
     * what the trail says was attached, whatever the rows of the evidences say now. A blob shared
     * by several entries is read once. The storage is only called when the trail references
     * evidence.
     */
    private fun verifyEvidence(export: TrailExport, verification: TrailVerification): TrailVerification {
        val missing = mutableListOf<Int>()
        val altered = mutableListOf<Int>()
        val checks = mutableMapOf<String, EvidenceBlobCheck>()
        export.entries.forEachIndexed { index, entry ->
            if (entry.type == TrailEntryTypes.EVIDENCE_ATTACHED) {
                val position = index + 1
                val sha256 = entry.payload.path("evidence").path("sha256")
                val check = if (sha256.isString && SHA256.matches(sha256.asString())) {
                    checks.getOrPut(sha256.asString()) { evidenceBlobStore.check(sha256.asString()) }
                } else {
                    // An entry which names no blob cannot have it
                    EvidenceBlobCheck.MISSING
                }
                when (check) {
                    EvidenceBlobCheck.OK -> {}
                    EvidenceBlobCheck.MISSING -> missing += position
                    EvidenceBlobCheck.ALTERED -> altered += position
                }
            }
        }
        return verification.copy(missingEvidence = missing, alteredEvidence = altered)
    }

    companion object {
        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}
