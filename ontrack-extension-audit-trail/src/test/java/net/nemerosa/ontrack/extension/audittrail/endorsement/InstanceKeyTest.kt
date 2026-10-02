package net.nemerosa.ontrack.extension.audittrail.endorsement

import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The instance key and its endorsements, against the shared test vector
 * `audit-trail/test-vectors/endorsements/01-rfc8032-test1.json`, produced with OpenSSL from the
 * published key of RFC 8032.
 */
class InstanceKeyTest {

    private val vector: JsonNode by lazy {
        InstanceKeyTest::class.java.getResource("/audit-trail/test-vectors/endorsements/01-rfc8032-test1.json")!!
            .readText(Charsets.UTF_8)
            .parseAsJson()
    }

    private val privateKeyPem: String get() = vector.path("privateKey").asString()

    private val privateKeyDer: ByteArray
        get() = Base64.getMimeDecoder().decode(
            privateKeyPem.lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")
        )

    @Test
    fun `Key ID of a key is the first 16 hex characters of the SHA-256 of its public key`() {
        val key = InstanceKey.parse(privateKeyPem.toByteArray())
        assertEquals("06e3fd8fda29bb60", key.keyId)
        assertEquals(vector.path("keyId").asString(), key.keyId)
    }

    @Test
    fun `Public key of a key is derived from its private key, in PEM`() {
        val key = InstanceKey.parse(privateKeyPem.toByteArray())
        assertEquals(TrailEndorsementFormat.ALGORITHM, key.algorithm)
        assertEquals("Ed25519", key.algorithm)
        assertEquals(vector.path("publicKey").asString(), key.publicKeyPem)
    }

    @Test
    fun `A key is read from its PKCS#8 form, in DER or in PEM`() {
        assertEquals(
            InstanceKey.parse(privateKeyPem.toByteArray()).keyId,
            InstanceKey.parse(privateKeyDer).keyId,
        )
    }

    @Test
    fun `Endorsement of a hash is the Ed25519 signature of its 32 bytes, in base64`() {
        val key = InstanceKey.parse(privateKeyPem.toByteArray())
        vector.path("endorsements").forEach { endorsement ->
            assertEquals(
                endorsement.path("signature").asString(),
                key.endorse(endorsement.path("hash").asString()),
                "Endorsement of ${endorsement.path("hash").asString()}"
            )
        }
    }

    @Test
    fun `An endorsement is verified against the public key alone`() {
        val publicKey = TrailEndorsementFormat.parsePublicKeyPem(vector.path("publicKey").asString())
        vector.path("endorsements").forEach { endorsement ->
            assertTrue(
                TrailEndorsementFormat.verify(
                    publicKey,
                    endorsement.path("hash").asString(),
                    endorsement.path("signature").asString(),
                )
            )
        }
    }

    @Test
    fun `An endorsement does not verify another hash, nor under another key`() {
        val (first, second) = vector.path("endorsements").toList()
        val publicKey = TrailEndorsementFormat.parsePublicKeyPem(vector.path("publicKey").asString())
        assertFalse(
            TrailEndorsementFormat.verify(publicKey, second.path("hash").asString(), first.path("signature").asString()),
            "Endorsement of another hash"
        )
        val other = InstanceKey.generate()
        assertFalse(
            TrailEndorsementFormat.verify(other.publicKey, first.path("hash").asString(), first.path("signature").asString()),
            "Endorsement by another key"
        )
        assertFalse(
            TrailEndorsementFormat.verify(publicKey, first.path("hash").asString(), "not base64!"),
            "Not a signature"
        )
    }

    @Test
    fun `A generated key survives its PKCS#8 form`() {
        val key = InstanceKey.generate()
        val read = InstanceKey.parse(key.toPkcs8())
        assertEquals(key.keyId, read.keyId)
        assertEquals(key.publicKeyPem, read.publicKeyPem)
        val hash = "4b320b3f1628780eaf5adbc9153975aa05eeb25fd082f97b111588499a6550a6"
        assertTrue(TrailEndorsementFormat.verify(key.publicKey, hash, read.endorse(hash)))
        assertTrue(Regex("[0-9a-f]{16}").matches(key.keyId), "Key ID is 16 lowercase hex characters")
    }

    @Test
    fun `Two generated keys differ`() {
        assertNotEquals(InstanceKey.generate().keyId, InstanceKey.generate().keyId)
    }

    @Test
    fun `Only the hash of an entry is endorsed`() {
        val key = InstanceKey.generate()
        assertThrows<IllegalArgumentException> { key.endorse("4B320B3F") }
        assertThrows<IllegalArgumentException> {
            key.endorse("4B320B3F1628780EAF5ADBC9153975AA05EEB25FD082F97B111588499A6550A6")
        }
    }

    @Test
    fun `A payload which is not an Ed25519 private key is rejected`() {
        assertThrows<InstanceKeyFormatException> { InstanceKey.parse("not a key".toByteArray()) }
        assertThrows<InstanceKeyFormatException> { InstanceKey.parse(ByteArray(256) { it.toByte() }) }
    }
}
