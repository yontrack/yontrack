package net.nemerosa.ontrack.extension.audittrail.endorsement

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.EdECPrivateKey
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * The instance key: the Ed25519 key pair with which this Yontrack endorses the entries of the
 * trails (see [TrailEndorsementFormat]).
 *
 * It is stored as its private key alone, in PKCS#8 — what
 * `openssl genpkey -algorithm ed25519` writes, in PEM or, with `-outform DER`, in DER. The public
 * key is derived from it.
 *
 * @property publicKey Public key
 */
class InstanceKey private constructor(
    private val privateKey: PrivateKey,
    val publicKey: PublicKey,
) {

    /**
     * Algorithm of the key
     */
    val algorithm: String = TrailEndorsementFormat.ALGORITHM

    /**
     * ID of the key, which every endorsement names
     */
    val keyId: String = TrailEndorsementFormat.keyId(publicKey)

    /**
     * Public key, in PEM
     */
    val publicKeyPem: String = TrailEndorsementFormat.publicKeyPem(publicKey)

    /**
     * Endorses the hash of an entry.
     *
     * @param hash Hash of the entry, 64 lowercase hex characters
     * @return Signature, in base64
     */
    fun endorse(hash: String): String = TrailEndorsementFormat.sign(privateKey, hash)

    /**
     * Form in which the key is stored: its private key in PKCS#8 DER.
     */
    fun toPkcs8(): ByteArray = privateKey.encoded

    override fun toString(): String = "InstanceKey(keyId=$keyId)"

    companion object {

        private const val SEED_SIZE = 32

        /**
         * Generates a new key.
         */
        fun generate(): InstanceKey {
            val generator = KeyPairGenerator.getInstance(TrailEndorsementFormat.ALGORITHM)
            val pair = generator.generateKeyPair()
            return InstanceKey(pair.private, pair.public)
        }

        /**
         * Reads a stored key.
         *
         * @param payload Private key in PKCS#8, DER or PEM (`-----BEGIN PRIVATE KEY-----`)
         * @return Key, its public key derived from its private key
         * @throws InstanceKeyFormatException When the payload is not an Ed25519 private key
         */
        fun parse(payload: ByteArray): InstanceKey {
            val der = if (isPem(payload)) {
                try {
                    TrailEndorsementFormat.pemBody(payload.toString(Charsets.US_ASCII))
                } catch (e: IllegalArgumentException) {
                    throw InstanceKeyFormatException(e)
                }
            } else {
                payload
            }
            val privateKey = try {
                KeyFactory.getInstance(TrailEndorsementFormat.ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(der))
            } catch (e: GeneralSecurityException) {
                throw InstanceKeyFormatException(e)
            }
            val seed = (privateKey as? EdECPrivateKey)?.bytes?.orElse(null)
                ?: throw InstanceKeyFormatException(null)
            return InstanceKey(privateKey, derivePublicKey(seed))
        }

        private fun isPem(payload: ByteArray): Boolean =
            payload.toString(Charsets.US_ASCII).trimStart().startsWith("-----BEGIN")

        /**
         * The JDK has no API deriving an Ed25519 public key from its private key, but its key
         * pair generator draws the 32 bytes of the private key (RFC 8032, 5.1.5) from its random
         * source and computes the public key from them: fed with the seed of a key, it gives
         * that key back.
         */
        private fun derivePublicKey(seed: ByteArray): PublicKey {
            if (seed.size != SEED_SIZE) throw InstanceKeyFormatException(null)
            val generator = KeyPairGenerator.getInstance(TrailEndorsementFormat.ALGORITHM)
            generator.initialize(NamedParameterSpec.ED25519, SeedRandom(seed))
            val pair = generator.generateKeyPair()
            val derivedSeed = (pair.private as EdECPrivateKey).bytes.orElseThrow()
            check(derivedSeed.contentEquals(seed)) { "Ed25519 public key could not be derived from its private key." }
            return pair.public
        }
    }

    /**
     * Random source returning the given seed, once.
     */
    private class SeedRandom(private val seed: ByteArray) : SecureRandom() {
        private var used = false

        override fun nextBytes(bytes: ByteArray) {
            check(!used && bytes.size == seed.size) { "Ed25519 public key could not be derived from its private key." }
            seed.copyInto(bytes)
            used = true
        }
    }
}
