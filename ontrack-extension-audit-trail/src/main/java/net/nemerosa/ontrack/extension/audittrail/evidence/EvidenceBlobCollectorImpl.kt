package net.nemerosa.ontrack.extension.audittrail.evidence

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class EvidenceBlobCollectorImpl(
    private val evidenceBlobStore: EvidenceBlobStore,
    private val evidenceRepository: EvidenceRepository,
    private val transactionTemplate: TransactionTemplate,
) : EvidenceBlobCollector {

    private val logger = LoggerFactory.getLogger(EvidenceBlobCollectorImpl::class.java)

    override fun collect(sha256: String): Boolean =
        transactionTemplate.execute {
            evidenceRepository.lockBlob(sha256)
            if (evidenceRepository.isBlobReferenced(sha256)) {
                false
            } else {
                evidenceBlobStore.remove(EvidenceBlobKeys.blob(sha256))
                true
            }
        } == true

    override fun sweep(writtenBefore: Instant): EvidenceSweep {
        var removedBlobs = 0
        evidenceBlobStore.list(EvidenceBlobKeys.BLOBS)
            .filter { it.lastModified.isBefore(writtenBefore) }
            .mapNotNull { EvidenceBlobKeys.sha256(it.key) }
            // One query for a page of blobs, the referenced ones being the most common
            .chunked(PAGE)
            .forEach { page ->
                val referenced = evidenceRepository.findReferencedBlobs(page)
                page.filterNot { it in referenced }.forEach { sha256 ->
                    // Checked again under the lock of the blob
                    if (collect(sha256)) {
                        removedBlobs++
                    }
                }
            }
        var removedUploads = 0
        evidenceBlobStore.list(EvidenceBlobKeys.UPLOADS)
            .filter { it.lastModified.isBefore(writtenBefore) && EvidenceBlobKeys.isKey(it.key) }
            .forEach {
                evidenceBlobStore.remove(it.key)
                removedUploads++
            }
        if (removedBlobs > 0 || removedUploads > 0) {
            logger.info("[audit-trail] Evidence storage swept: {} blob(s) and {} upload(s) removed", removedBlobs, removedUploads)
        }
        return EvidenceSweep(removedBlobs = removedBlobs, removedUploads = removedUploads)
    }

    companion object {
        private const val PAGE = 500
    }
}
