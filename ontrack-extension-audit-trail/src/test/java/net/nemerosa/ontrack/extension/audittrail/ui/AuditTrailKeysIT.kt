package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyStatus
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import net.nemerosa.ontrack.extension.audittrail.endorsement.TrailEndorsementFormat
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The public key of the instance, on `GET /rest/extension/audit-trail/keys` and in GraphQL.
 */
class AuditTrailKeysIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var auditTrailKeysController: AuditTrailKeysController

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    private fun instanceKey(): InstancePublicKey {
        assertEquals(InstanceKeyStatus.OK, instanceKeyService.keyStatus)
        return instanceKeyService.getPublicKeys().single()
    }

    @Test
    fun `Public key of the instance on the REST API`() {
        val key = instanceKey()
        val keys = asUser { auditTrailKeysController.getKeys() }
        assertEquals(listOf(key), keys)
        val (published) = keys
        assertEquals("Ed25519", published.algorithm)
        assertTrue(Regex("[0-9a-f]{16}").matches(published.keyId), "Key ID")
        assertTrue(published.publicKey.startsWith("-----BEGIN PUBLIC KEY-----\n"), "PEM")
        assertEquals(
            published.keyId,
            TrailEndorsementFormat.keyId(TrailEndorsementFormat.parsePublicKeyPem(published.publicKey)),
        )
    }

    @Test
    fun `Public key of the instance in GraphQL, for any user`() {
        val key = instanceKey()
        asUser {
            run(
                """
                    {
                        auditTrailKeys {
                            keyId
                            algorithm
                            publicKey
                        }
                    }
                """
            ) { data ->
                val keys = data.path("auditTrailKeys")
                assertEquals(1, keys.size())
                val published = keys.path(0)
                assertEquals(key.keyId, published.path("keyId").asString())
                assertEquals("Ed25519", published.path("algorithm").asString())
                assertEquals(key.publicKey, published.path("publicKey").asString())
            }
        }
    }
}
