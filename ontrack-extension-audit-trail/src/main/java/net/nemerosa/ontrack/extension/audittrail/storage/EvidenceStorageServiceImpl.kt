package net.nemerosa.ontrack.extension.audittrail.storage

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.time.Duration

@Service
class EvidenceStorageServiceImpl(
    auditTrailConfigProperties: AuditTrailConfigProperties,
) : EvidenceStorageService, DisposableBean {

    private val logger = LoggerFactory.getLogger(EvidenceStorageServiceImpl::class.java)

    private val storage = auditTrailConfigProperties.storage

    private val clientHolder = lazy {
        if (storage.missingProperties().isEmpty()) {
            EvidenceStorageClient(
                s3 = createEvidenceStorageS3Client(storage),
                bucket = requireNotNull(storage.bucket),
            )
        } else {
            null
        }
    }

    override val client: EvidenceStorageClient? by clientHolder

    @Volatile
    private var cachedStatus: EvidenceStorageStatus? = null

    override val status: EvidenceStorageStatus
        get() = cachedStatus ?: synchronized(this) {
            cachedStatus ?: checkStatus()
        }

    override fun checkStatus(): EvidenceStorageStatus {
        val status = probe()
        val previous = cachedStatus
        cachedStatus = status
        if (previous?.state != status.state) {
            if (status.state == EvidenceStorageState.OK) {
                logger.info("[audit-trail] Evidence storage OK")
            } else {
                logger.warn("[audit-trail] Evidence storage ${status.state}: ${status.message}")
            }
        }
        return status
    }

    private fun probe(): EvidenceStorageStatus {
        val missing = storage.missingProperties()
        if (missing.isNotEmpty()) {
            return status(
                EvidenceStorageState.NOT_CONFIGURED,
                "Missing properties: ${AuditTrailConfigProperties.PREFIX}.storage.${
                    missing.joinToString(", ", prefix = "{", postfix = "}")
                }"
            )
        }
        val bucket = storage.bucket
        return try {
            val client = requireNotNull(client)
            client.s3.headBucket(
                HeadBucketRequest.builder()
                    .bucket(client.bucket)
                    .overrideConfiguration { it.apiCallTimeout(PROBE_TIMEOUT) }
                    .build()
            )
            status(EvidenceStorageState.OK, null)
        } catch (_: NoSuchBucketException) {
            status(EvidenceStorageState.UNREACHABLE, "The bucket $bucket does not exist.")
        } catch (e: S3Exception) {
            val message = when (e.statusCode()) {
                403 -> "Access to the bucket $bucket is denied (HTTP 403): check the credentials."
                404 -> "The bucket $bucket does not exist."
                else -> "The bucket $bucket cannot be read (HTTP ${e.statusCode()}): ${e.message}"
            }
            status(EvidenceStorageState.UNREACHABLE, message)
        } catch (e: SdkClientException) {
            status(EvidenceStorageState.UNREACHABLE, "The storage cannot be reached at ${storage.endpoint}: ${e.message}")
        } catch (e: Exception) {
            status(EvidenceStorageState.UNREACHABLE, "The storage cannot be used: ${e.message}")
        }
    }

    private fun status(state: EvidenceStorageState, message: String?) =
        EvidenceStorageStatus(state = state, message = message, checkedAt = Time.now())

    override fun destroy() {
        if (clientHolder.isInitialized()) {
            clientHolder.value?.s3?.close()
        }
    }

    companion object {
        /**
         * Time given to a probe, retries included
         */
        private val PROBE_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
