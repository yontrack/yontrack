package net.nemerosa.ontrack.extension.audittrail.service

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKey
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyServiceImpl
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyStatus
import net.nemerosa.ontrack.extension.audittrail.endorsement.TrailEndorsementFormat
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEndorsementRepository
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEntryRepository
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.AbstractConfidentialStore
import net.nemerosa.ontrack.model.security.ConfidentialStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Endorsement of the entries by the instance key, with the key store of the instance — writable —
 * and with a read-only key store which has no key.
 */
class TrailEndorsementIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    @Autowired
    private lateinit var confidentialStore: ConfidentialStore

    @Autowired
    private lateinit var trailEntryRepository: TrailEntryRepository

    @Autowired
    private lateinit var trailEndorsementRepository: TrailEndorsementRepository

    @Autowired
    private lateinit var auditTrailLicense: AuditTrailLicense

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val ci: JsonNode = mapOf("account" to "ci-bot", "via" to "token", "tokenName" to "ci-demo").asJson()

    /**
     * A key store with no key, which refuses to store one, like the `secret` store.
     */
    private class ReadOnlyStore : AbstractConfidentialStore() {
        override fun store(key: String, payload: ByteArray) {
            throw IllegalStateException("Read-only store")
        }

        override fun load(key: String): ByteArray? = null
    }

    @Test
    fun `Every entry is endorsed by the instance key, over its hash`() {
        asAdmin {
            project {
                branch {
                    build {
                        // trail.opened, then validation.run
                        trailService.append(this, "validation.run", mapOf("status" to "PASSED").asJson(), ci)
                        trailService.append(this, "promotion.added", mapOf("promotionLevel" to "GOLD").asJson(), ci)

                        val entries = trailService.getEntries(this)
                        val endorsements = trailService.getEndorsements(this)
                        assertEquals(3, entries.size)
                        assertEquals(entries.map { it.id }, endorsements.map { it.entryId }, "One endorsement per entry")

                        val publicKey = instanceKeyService.getPublicKeys().single()
                        assertEquals("Ed25519", publicKey.algorithm)
                        val key = TrailEndorsementFormat.parsePublicKeyPem(publicKey.publicKey)
                        entries.zip(endorsements).forEach { (entry, endorsement) ->
                            assertEquals(publicKey.keyId, endorsement.keyId)
                            assertEquals(entry.time, endorsement.time)
                            assertTrue(
                                TrailEndorsementFormat.verify(key, entry.hash, endorsement.signature),
                                "Endorsement of seq ${entry.seq} verified against the published key"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `The instance key is generated once, in the key store, and used again after a restart`() {
        val keyId = instanceKeyService.getPublicKeys().firstOrNull()?.keyId
            ?: run {
                assertEquals(InstanceKeyStatus.OK, instanceKeyService.keyStatus)
                instanceKeyService.getPublicKeys().single().keyId
            }

        val stored = assertNotNull(confidentialStore.load("audit-trail.ed25519"), "Key in the key store")
        assertEquals(keyId, InstanceKey.parse(stored).keyId)

        // A new service on the same store, as after a restart
        val restarted = InstanceKeyServiceImpl(confidentialStore)
        assertEquals(instanceKeyService.getPublicKeys(), restarted.getPublicKeys())
        assertEquals(keyId, restarted.endorse("4b320b3f1628780eaf5adbc9153975aa05eeb25fd082f97b111588499a6550a6")?.keyId)
        assertEquals(InstanceKeyStatus.OK, restarted.keyStatus)
        val storedAgain = assertNotNull(confidentialStore.load("audit-trail.ed25519"))
        assertEquals(keyId, InstanceKey.parse(storedAgain).keyId, "Key not replaced")
    }

    @Test
    fun `With a read-only key store and no key, entries are written unendorsed and the key is reported as not provisioned`() {
        val readOnlyKeyService = InstanceKeyServiceImpl(ReadOnlyStore())
        val readOnlyTrailService = TrailServiceImpl(
            trailEntryRepository = trailEntryRepository,
            trailEndorsementRepository = trailEndorsementRepository,
            auditTrailLicense = auditTrailLicense,
            securityService = securityService,
            meterRegistry = meterRegistry,
            instanceKeyService = readOnlyKeyService,
        )
        asAdmin {
            project {
                branch {
                    build {
                        readOnlyTrailService.append(this, TrailEntryTypes.BUILD_CREATED, mapOf("build" to mapOf("id" to id())).asJson(), ci)
                        readOnlyTrailService.append(this, "validation.run", mapOf("status" to "PASSED").asJson(), ci)

                        assertEquals(InstanceKeyStatus.NOT_PROVISIONED, readOnlyKeyService.keyStatus)
                        assertEquals(emptyList(), readOnlyKeyService.getPublicKeys())
                        assertEquals(2, trailService.getEntries(this).size, "Entries written")
                        assertEquals(emptyList(), trailService.getEndorsements(this), "Entries unendorsed")

                        // Once a key is there, the next entries are endorsed, never the past ones
                        trailService.append(this, "promotion.added", mapOf("promotionLevel" to "GOLD").asJson(), ci)
                        val entries = trailService.getEntries(this)
                        assertEquals(
                            listOf(entries.last().id),
                            trailService.getEndorsements(this).map { it.entryId },
                            "Endorsed from seq 3 on"
                        )
                    }
                }
            }
        }
    }
}
