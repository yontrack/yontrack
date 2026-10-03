package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageClient
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@Component
class EvidenceBlobStoreImpl(
    private val evidenceStorageService: EvidenceStorageService,
    private val auditTrailConfigProperties: AuditTrailConfigProperties,
) : EvidenceBlobStore {

    private val logger = LoggerFactory.getLogger(EvidenceBlobStoreImpl::class.java)

    override fun stage(content: InputStream, size: Long, expectedSha256: String?): EvidenceStagedBlob {
        val maxSize = auditTrailConfigProperties.storage.maxSize.toBytes()
        if (size > maxSize) {
            throw tooLarge(maxSize)
        }
        val client = client()
        val uploadKey = EvidenceBlobKeys.upload(UUID.randomUUID())
        val digest = EvidenceDigestInputStream(content, maxSize)
        var staged = false
        try {
            var provided = false
            client.s3.putObject(
                PutObjectRequest.builder()
                    .bucket(client.bucket)
                    .key(uploadKey)
                    .contentLength(size)
                    .contentType(EvidenceDisposition.ATTACHMENT_CONTENT_TYPE)
                    .build(),
                // The content is read once: a retry of the SDK fails rather than sending other bytes.
                // The SDK closes what it reads, but the content is checked to its end afterwards,
                // and closed by its owner.
                RequestBody.fromContentProvider(
                    {
                        check(!provided) { "The content of an evidence cannot be sent twice." }
                        provided = true
                        object : FilterInputStream(digest) {
                            override fun close() {}
                        }
                    },
                    size,
                    EvidenceDisposition.ATTACHMENT_CONTENT_TYPE,
                ),
            )
            // The SDK reads the declared size only: anything beyond is refused
            if (digest.size != size || digest.read() >= 0) {
                throw invalidSize(size)
            }
            val sha256 = digest.sha256
            if (expectedSha256 != null && expectedSha256 != sha256) {
                throw EvidenceException(
                    EvidenceError.DIGEST_MISMATCH,
                    "The SHA-256 of the evidence is $sha256, not the claimed $expectedSha256."
                )
            }
            staged = true
            return EvidenceStagedBlob(uploadKey = uploadKey, sha256 = sha256, size = size)
        } catch (e: SdkException) {
            // Refusals raised while the SDK was reading the content
            e.evidenceCause()?.let { throw it }
            throw unreachable("The evidence cannot be stored", e)
        } finally {
            // A refused content leaves nothing behind
            if (!staged) {
                delete(client, uploadKey)
            }
        }
    }

    override fun persist(staged: EvidenceStagedBlob): EvidenceBlob {
        val client = client()
        try {
            client.s3.copyObject(
                CopyObjectRequest.builder()
                    .sourceBucket(client.bucket)
                    .sourceKey(staged.uploadKey)
                    .destinationBucket(client.bucket)
                    .destinationKey(EvidenceBlobKeys.blob(staged.sha256))
                    .build()
            )
        } catch (e: SdkException) {
            throw unreachable("The evidence cannot be stored", e)
        }
        return EvidenceBlob(sha256 = staged.sha256, size = staged.size)
    }

    override fun discard(staged: EvidenceStagedBlob) {
        val client = evidenceStorageService.client ?: return
        delete(client, staged.uploadKey)
    }

    override fun open(sha256: String): EvidenceBlobContent? {
        val client = client()
        return try {
            val response = client.s3.getObject(
                GetObjectRequest.builder()
                    .bucket(client.bucket)
                    .key(EvidenceBlobKeys.blob(sha256))
                    .build()
            )
            EvidenceBlobContent(stream = response, size = response.response().contentLength())
        } catch (_: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) {
                null
            } else {
                throw unreachable("The evidence cannot be read", e)
            }
        } catch (e: SdkException) {
            throw unreachable("The evidence cannot be read", e)
        }
    }

    override fun check(sha256: String): EvidenceBlobCheck {
        val content = open(sha256) ?: return EvidenceBlobCheck.MISSING
        val digest = EvidenceDigestInputStream(content.stream, Long.MAX_VALUE)
        try {
            digest.use { it.transferTo(OutputStream.nullOutputStream()) }
        } catch (e: SdkException) {
            throw unreachable("The evidence cannot be read", e)
        }
        return if (digest.sha256 == sha256) EvidenceBlobCheck.OK else EvidenceBlobCheck.ALTERED
    }

    override fun list(prefix: String): Sequence<EvidenceStoredObject> {
        require(prefix == EvidenceBlobKeys.BLOBS || prefix == EvidenceBlobKeys.UPLOADS) {
            "Not a prefix of the evidence: $prefix"
        }
        val client = client()
        return sequence {
            var token: String? = null
            do {
                val response = try {
                    client.s3.listObjectsV2(
                        ListObjectsV2Request.builder()
                            .bucket(client.bucket)
                            .prefix(prefix)
                            .continuationToken(token)
                            .build()
                    )
                } catch (e: SdkException) {
                    throw unreachable("The evidence storage cannot be listed", e)
                }
                response.contents().forEach { o ->
                    yield(EvidenceStoredObject(key = o.key(), lastModified = o.lastModified()))
                }
                token = if (response.isTruncated == true) response.nextContinuationToken() else null
            } while (token != null)
        }
    }

    override fun remove(key: String) {
        require(EvidenceBlobKeys.isKey(key)) { "Not a key of the evidence: $key" }
        val client = client()
        try {
            client.s3.deleteObject(DeleteObjectRequest.builder().bucket(client.bucket).key(key).build())
        } catch (e: SdkException) {
            throw unreachable("The evidence cannot be removed", e)
        }
    }

    /**
     * Client of the storage, when it is configured and was not found unreachable.
     */
    private fun client(): EvidenceStorageClient {
        val client = evidenceStorageService.client
            ?: throw EvidenceException(
                EvidenceError.STORAGE_NOT_CONFIGURED,
                "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached."
            )
        val status = evidenceStorageService.status
        if (status.state == EvidenceStorageState.UNREACHABLE) {
            throw EvidenceException(
                EvidenceError.STORAGE_UNREACHABLE,
                "The evidence storage cannot be reached: ${status.message}"
            )
        }
        return client
    }

    private fun delete(client: EvidenceStorageClient, key: String) {
        try {
            client.s3.deleteObject(DeleteObjectRequest.builder().bucket(client.bucket).key(key).build())
        } catch (e: SdkException) {
            // Left for the sweep
            logger.warn("[audit-trail] Upload $key could not be deleted: ${e.message}")
        }
    }

    private fun unreachable(what: String, e: SdkException): EvidenceException {
        logger.error("[audit-trail] $what: ${e.message}", e)
        return EvidenceException(EvidenceError.STORAGE_UNREACHABLE, "$what: the evidence storage failed to answer.")
    }

    private fun tooLarge(maxSize: Long) = EvidenceException(
        EvidenceError.TOO_LARGE,
        "The evidence is bigger than the maximum size of $maxSize bytes."
    )

    private fun invalidSize(size: Long) = EvidenceException(
        EvidenceError.INVALID,
        "The content of the evidence is not of its declared size of $size bytes."
    )

    private fun Throwable.evidenceCause(): EvidenceException? =
        generateSequence(this as Throwable?) { it.cause }.filterIsInstance<EvidenceException>().firstOrNull()
}
