package net.nemerosa.ontrack.extension.audittrail

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DataSizeUnit
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit

/**
 * Configuration of the audit trail.
 */
@ConfigurationProperties(prefix = AuditTrailConfigProperties.PREFIX)
@Component
@APIName("Audit trail configuration")
@APIDescription("Configuration of the audit trail, and of the storage of its evidence.")
class AuditTrailConfigProperties {

    /**
     * Storage of the evidence
     */
    var storage = StorageProperties()

    /**
     * Storage of the evidence: an S3-compatible bucket — MinIO, AWS S3, DigitalOcean Spaces...
     *
     * The storage is configured when its endpoint, bucket, access key and secret key are all set.
     * The credentials are only ever read from the configuration, never from the database.
     */
    class StorageProperties {

        @APIDescription("URL of the S3-compatible service storing the evidence, for example `https://fra1.digitaloceanspaces.com` or `http://minio:9000`. Required.")
        var endpoint: String? = null

        @APIDescription("Name of the bucket storing the evidence. It must exist. Required.")
        var bucket: String? = null

        @APIDescription("Region of the bucket. MinIO accepts any region; DigitalOcean Spaces wants the region of the endpoint, for example `fra1`.")
        var region: String = DEFAULT_REGION

        @APIDescription("Path-style addressing (`<endpoint>/<bucket>`), as MinIO needs, instead of the virtual-hosted one (`<bucket>.<endpoint>`), as AWS S3 and DigitalOcean Spaces use.")
        var pathStyle: Boolean = false

        @APIDescription("Access key of the S3-compatible service. Required.")
        var accessKey: String? = null

        @APIDescription("Secret key of the S3-compatible service. Required.")
        var secretKey: String? = null

        @APIDescription("Maximum size of one evidence, for the whole instance. In megabytes when no unit is given.")
        @DataSizeUnit(DataUnit.MEGABYTES)
        var maxSize: DataSize = DataSize.ofMegabytes(DEFAULT_MAX_SIZE_MB)

        /**
         * Names of the required properties which are not set, empty when the storage is configured.
         *
         * A function, not a property: the documentation of the configuration lists the properties.
         */
        fun missingProperties(): List<String> =
            listOfNotNull(
                "endpoint".takeIf { endpoint.isNullOrBlank() },
                "bucket".takeIf { bucket.isNullOrBlank() },
                "access-key".takeIf { accessKey.isNullOrBlank() },
                "secret-key".takeIf { secretKey.isNullOrBlank() },
            )
    }

    companion object {
        /**
         * Prefix of the properties of the audit trail
         */
        const val PREFIX = "ontrack.extension.audit-trail"

        /**
         * Default region of the evidence storage, the one MinIO uses
         */
        const val DEFAULT_REGION = "us-east-1"

        /**
         * Default maximum size of one evidence, in megabytes
         */
        const val DEFAULT_MAX_SIZE_MB = 50L
    }
}
