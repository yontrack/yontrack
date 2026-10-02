package net.nemerosa.ontrack.extension.audittrail.endorsement

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.HexFormat

/**
 * How the entries of a trail are endorsed by the instance key.
 *
 * **This format is a compatibility contract**, like the hash format: every endorsement ever
 * written is verified with it, by Yontrack and offline by `yontrack audit-trail verify`.
 *
 * - **Algorithm** — Ed25519 (RFC 8032), as built in the JDK.
 * - **Signed bytes** — the 32 bytes of the hash of the entry: its 64 lowercase hexadecimal
 *   characters, decoded. Nothing else is signed: the hash covers the entry and, through `prevHash`,
 *   the trail before it.
 * - **Signature** — the 64 bytes of the Ed25519 signature, in standard base64 with padding
 *   (88 characters).
 * - **Public key** — the DER `SubjectPublicKeyInfo` of the key (RFC 8410), published in PEM as
 *   `-----BEGIN PUBLIC KEY-----`.
 * - **Key ID** — the first 16 lowercase hexadecimal characters of the SHA-256 of that DER
 *   `SubjectPublicKeyInfo`: what the base64 body of the PEM decodes to.
 *
 * The shared test vector `audit-trail/test-vectors/endorsements/01-rfc8032-test1.json` endorses
 * the entries of `01-build-created.json` with the published key of RFC 8032.
 */
object TrailEndorsementFormat {

    /**
     * Algorithm of the endorsements, as named by the JDK and in the public keys published by Yontrack.
     */
    const val ALGORITHM = "Ed25519"

    private const val KEY_ID_LENGTH = 16

    private val HASH_REGEX = Regex("[0-9a-f]{64}")

    /**
     * Key ID of a public key.
     *
     * @param publicKey Ed25519 public key
     * @return First 16 lowercase hex characters of the SHA-256 of its DER `SubjectPublicKeyInfo`
     */
    fun keyId(publicKey: PublicKey): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey.encoded))
            .take(KEY_ID_LENGTH)

    /**
     * PEM form of a public key.
     *
     * @param publicKey Ed25519 public key
     * @return `-----BEGIN PUBLIC KEY-----` block, lines of 64 characters, ending with a new line
     */
    fun publicKeyPem(publicKey: PublicKey): String = buildString {
        append("-----BEGIN PUBLIC KEY-----\n")
        Base64.getEncoder().encodeToString(publicKey.encoded).chunked(64).forEach { append(it).append('\n') }
        append("-----END PUBLIC KEY-----\n")
    }

    /**
     * Reads a public key from its PEM form.
     *
     * @param pem `-----BEGIN PUBLIC KEY-----` block of an Ed25519 key
     * @return Public key
     */
    fun parsePublicKeyPem(pem: String): PublicKey =
        KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(pemBody(pem)))

    /**
     * Endorses the hash of an entry.
     *
     * @param privateKey Ed25519 private key
     * @param hash Hash of the entry, 64 lowercase hex characters
     * @return Signature, in base64
     */
    fun sign(privateKey: PrivateKey, hash: String): String {
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(privateKey)
        signature.update(hashBytes(hash))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    /**
     * Checks the endorsement of the hash of an entry.
     *
     * @param publicKey Ed25519 public key whose ID the endorsement names
     * @param hash Hash of the entry, 64 lowercase hex characters
     * @param signature Signature, in base64
     * @return `true` when the signature is the endorsement of this hash by this key
     */
    fun verify(publicKey: PublicKey, hash: String, signature: String): Boolean {
        val signatureBytes = try {
            Base64.getDecoder().decode(signature)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val verifier = Signature.getInstance(ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(hashBytes(hash))
        return try {
            verifier.verify(signatureBytes)
        } catch (_: java.security.SignatureException) {
            false
        }
    }

    private fun hashBytes(hash: String): ByteArray {
        require(HASH_REGEX.matches(hash)) { "Only the hash of an entry is endorsed: 64 lowercase hex characters." }
        return HexFormat.of().parseHex(hash)
    }

    /**
     * Decodes the base64 body of a PEM block.
     */
    internal fun pemBody(pem: String): ByteArray =
        Base64.getMimeDecoder().decode(
            pem.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("-----") }
                .joinToString("")
        )
}
