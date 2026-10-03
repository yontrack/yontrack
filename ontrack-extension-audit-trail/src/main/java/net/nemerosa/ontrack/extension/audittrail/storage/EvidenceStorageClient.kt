package net.nemerosa.ontrack.extension.audittrail.storage

import software.amazon.awssdk.services.s3.S3Client

/**
 * Client of the evidence storage: the S3 client and the bucket it stores the evidence into.
 *
 * @property s3 S3 client, built for S3-compatible services: checksums only when required
 * @property bucket Name of the bucket
 */
class EvidenceStorageClient(
    val s3: S3Client,
    val bucket: String,
)
