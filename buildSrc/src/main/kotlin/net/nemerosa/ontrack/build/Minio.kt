package net.nemerosa.ontrack.build

/**
 * The S3-compatible object storage of the local stacks (#1962): the development stack, the
 * integration test stack ([ItStack]) and the KDSL acceptance stack ([KdslStack]) each run one,
 * with its bucket created at start, for the evidence of the audit trail.
 *
 * The image is a MinIO fork, not MinIO itself: MinIO Inc. stopped publishing community images in
 * October 2025, archived `minio/minio` in 2026, and the `minio/minio` image has since left Docker
 * Hub. `pgsty/minio`, the community fork, now publishes as SILO (`pgsty/silo`): same `MINIO_*`
 * environment, same S3 API, same `/data` layout, with the `mc` client (`mcli`) bundled in the
 * image, which is what creates the bucket.
 *
 * The values below are repeated, literally, in `compose/docker-compose-{dev,it,kdsl}.yml` and in
 * `scripts/dev-stack.sh`, which cannot read them; `MinioTest` keeps them in step.
 */
object Minio {

    /**
     * Pinned by release *and* digest. Bumping it means changing the four places `MinioTest`
     * checks, and nothing else.
     */
    const val IMAGE =
        "pgsty/silo:RELEASE.2026-09-16T00-00-00Z@sha256:635197cb9f36d01bee221d34d1c7d7960f6a95c48b0b6c01d99cd13bdae51a46"

    /**
     * The host port of the S3 API in slot 0 -- MinIO's own 9000, moved up to a range nothing else
     * in the stacks comes near: over ten slots it spans 19000-19900, well above RabbitMQ's
     * management console (15672-16572), the highest port any stack publishes. The development
     * stack also publishes the web console on the next port, 19001.
     */
    const val BASE_PORT = 19000

    const val BUCKET = "yontrack-audit-trail"
    const val REGION = "us-east-1"
    const val ACCESS_KEY = "yontrack-minio"
    const val SECRET_KEY = "yontrack-minio-secret"

    /**
     * The `ontrack.extension.audit-trail.storage.*` properties that point Yontrack at the bucket
     * behind [endpoint]. MinIO is addressed path-style: a bucket host name such as
     * `yontrack-audit-trail.localhost` resolves nowhere.
     */
    fun storageProperties(endpoint: String): Map<String, String> = mapOf(
        "ontrack.extension.audit-trail.storage.endpoint" to endpoint,
        "ontrack.extension.audit-trail.storage.bucket" to BUCKET,
        "ontrack.extension.audit-trail.storage.region" to REGION,
        "ontrack.extension.audit-trail.storage.path-style" to "true",
        "ontrack.extension.audit-trail.storage.access-key" to ACCESS_KEY,
        "ontrack.extension.audit-trail.storage.secret-key" to SECRET_KEY,
    )
}
