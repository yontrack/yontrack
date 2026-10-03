package net.nemerosa.ontrack.extension.audittrail.evidence

import java.util.*

/**
 * Keys of the evidence in the bucket.
 *
 * - `blobs/<sha256>` — the content of an evidence, addressed by its SHA-256 as the server computed
 *   it: two evidences with the same content share one blob, and nothing a client sends — a file
 *   name, a claimed digest — ever reaches a key;
 * - `uploads/<uuid>` — an upload in progress, whose SHA-256 is not known yet: it is copied to its
 *   blob once hashed, and deleted.
 */
object EvidenceBlobKeys {

    /**
     * Prefix of the blobs
     */
    const val BLOBS = "blobs/"

    /**
     * Prefix of the uploads in progress
     */
    const val UPLOADS = "uploads/"

    private val SHA256 = Regex("^[0-9a-f]{64}$")

    /**
     * Key of the blob of a content.
     *
     * @param sha256 SHA-256 of the content, in lowercase hexadecimal
     * @throws IllegalArgumentException When the value is not a SHA-256 in lowercase hexadecimal
     */
    fun blob(sha256: String): String {
        require(SHA256.matches(sha256)) { "Not a SHA-256 in lowercase hexadecimal: $sha256" }
        return BLOBS + sha256
    }

    /**
     * Key of an upload in progress.
     */
    fun upload(id: UUID): String = UPLOADS + id

    private val BLOB_KEY = Regex("^blobs/([0-9a-f]{64})$")

    private val UPLOAD_KEY = Regex("^uploads/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    /**
     * SHA-256 of the content of a blob, read from its key — `null` when the key is not the one of
     * a blob.
     */
    fun sha256(key: String): String? = BLOB_KEY.matchEntire(key)?.groupValues?.get(1)

    /**
     * Whether a key is the one of a blob or of an upload, and nothing else.
     */
    fun isKey(key: String): Boolean = BLOB_KEY.matches(key) || UPLOAD_KEY.matches(key)
}
