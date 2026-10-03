package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import java.time.Instant
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sweep of the evidence storage: the blobs which no evidence references any longer, and the
 * uploads left behind, once older than the delay.
 *
 * The bucket of the stack is shared with the other tests: every sweep here only removes what was
 * written up to the second of the objects of its test, never a later upload.
 */
class EvidenceSweepIT : AbstractEvidenceITSupport() {

    @Autowired
    private lateinit var evidenceBlobCollector: EvidenceBlobCollector

    @Autowired
    private lateinit var evidenceSweepJob: EvidenceSweepJob

    /**
     * When an object was last written, by the storage's clock.
     */
    private fun lastModified(key: String): Instant =
        client.s3.headObject(HeadObjectRequest.builder().bucket(client.bucket).key(key).build()).lastModified()

    /**
     * Sweeps what was written up to the time of the object of a key, included — as if the delay
     * had elapsed for it.
     */
    private fun sweepUpTo(key: String): EvidenceSweep =
        evidenceBlobCollector.sweep(writtenBefore = lastModified(key).plusSeconds(1))

    /**
     * An orphan blob — no evidence references its content.
     */
    private fun orphan(): String {
        val content = pdf()
        val sha256 = sha256(content)
        put("blobs/$sha256", content)
        return sha256
    }

    @Test
    fun `Sweep removes an orphan blob older than the delay`() {
        val sha256 = orphan()
        val sweep = sweepUpTo("blobs/$sha256")
        assertFalse(exists("blobs/$sha256"), "Orphan removed")
        assertTrue(sweep.removedBlobs >= 1)
    }

    @Test
    fun `Sweep keeps a recent orphan blob`() {
        val sha256 = orphan()
        evidenceBlobCollector.sweep(writtenBefore = lastModified("blobs/$sha256"))
        assertTrue(exists("blobs/$sha256"), "Recent orphan kept")
    }

    @Test
    fun `The sweep job keeps the orphans of the last 24 hours`() {
        val sha256 = orphan()
        assertNotNull(evidenceSweepJob.sweep(), "Storage of the stack available")
        assertTrue(exists("blobs/$sha256"), "Recent orphan kept")
    }

    @Test
    fun `Sweep keeps a blob an evidence references`() {
        validationRun {
            val evidence = upload(pdf())
            sweepUpTo("blobs/${evidence.sha256}")
            assertTrue(exists("blobs/${evidence.sha256}"), "Referenced blob kept")
        }
    }

    @Test
    fun `Sweep keeps a blob referenced by an evidence when another one with the same content is deleted`() {
        validationRun {
            val content = pdf()
            val first = upload(content)
            upload(content, fileName = "again.pdf")
            asAdmin { evidenceService.delete(first.id) }
            sweepUpTo("blobs/${first.sha256}")
            assertTrue(exists("blobs/${first.sha256}"), "Blob kept for the second evidence")
        }
    }

    @Test
    fun `Sweep removes the blob of an evidence gone with its validation run`() {
        validationRun {
            val evidence = upload(pdf())
            asAdmin { structureService.deleteValidationRun(this) }
            assertTrue(evidenceRepository.findById(evidence.id) == null, "Row gone with its run")
            assertTrue(exists("blobs/${evidence.sha256}"), "Blob left to the sweep")
            sweepUpTo("blobs/${evidence.sha256}")
            assertFalse(exists("blobs/${evidence.sha256}"), "Blob swept")
        }
    }

    @Test
    fun `Sweep removes the blob of an evidence gone with its build`() {
        validationRun {
            val evidence = upload(pdf())
            asAdmin { structureService.deleteBuild(build.id) }
            assertTrue(evidenceRepository.findById(evidence.id) == null, "Row gone with its build")
            sweepUpTo("blobs/${evidence.sha256}")
            assertFalse(exists("blobs/${evidence.sha256}"), "Blob swept")
        }
    }

    @Test
    fun `Sweep removes the uploads left behind older than the delay, and only them`() {
        val upload = EvidenceBlobKeys.upload(UUID.randomUUID())
        put(upload, uid("upload-").toByteArray())
        evidenceBlobCollector.sweep(writtenBefore = lastModified(upload))
        assertTrue(exists(upload), "Recent upload kept")
        val sweep = sweepUpTo(upload)
        assertFalse(exists(upload), "Upload left behind removed")
        assertTrue(sweep.removedUploads >= 1)
    }

    @Test
    fun `Sweep leaves what is not a key of the evidence`() {
        val key = "uploads/not-an-upload-${uid("k-")}"
        put(key, uid("other-").toByteArray())
        try {
            sweepUpTo(key)
            assertTrue(exists(key), "Not a key of the evidence: left alone")
        } finally {
            client.s3.deleteObject { it.bucket(client.bucket).key(key) }
        }
    }

}
