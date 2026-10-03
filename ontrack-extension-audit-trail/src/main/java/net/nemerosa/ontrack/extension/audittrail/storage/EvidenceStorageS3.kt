package net.nemerosa.ontrack.extension.audittrail.storage

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.http.apache5.Apache5HttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

/**
 * Builds the S3 client of the evidence storage.
 *
 * Checksums are computed and validated only when an operation requires them: the SDK sends CRC
 * checksums by default since 2.30, which some S3-compatible services reject. The addressing is
 * path-style or virtual-hosted, as configured: MinIO needs the former, DigitalOcean Spaces uses
 * the latter.
 *
 * @param storage Configuration of the storage, which must be complete
 */
internal fun createEvidenceStorageS3Client(storage: AuditTrailConfigProperties.StorageProperties): S3Client =
    S3Client.builder()
        .httpClientBuilder(Apache5HttpClient.builder())
        .endpointOverride(URI.create(requireNotNull(storage.endpoint)))
        .region(Region.of(storage.region))
        .forcePathStyle(storage.pathStyle)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    requireNotNull(storage.accessKey),
                    requireNotNull(storage.secretKey),
                )
            )
        )
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build()
