package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.TrailEndorsementFormat
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Build.auditTrail` in GraphQL: entries, endorsements and verification — of untouched trails,
 * and of trails whose rows were tampered with in the database.
 */
class BuildAuditTrailGraphQLIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    private val query = """
        query AuditTrail(${'$'}id: Int!, ${'$'}includeEvidence: Boolean) {
            build(id: ${'$'}id) {
                auditTrail {
                    entries {
                        id
                        seq
                        schemaVersion
                        type
                        time
                        actor
                        payload
                        prevHash
                        hash
                    }
                    endorsements {
                        entryId
                        seq
                        keyId
                        signature
                        time
                    }
                    verification(includeEvidence: ${'$'}includeEvidence) {
                        chainIntact
                        firstBrokenSeq
                        endorsementsValid
                        firstInvalidEndorsementSeq
                        partial
                        unendorsedFromSeq
                        problems {
                            seq
                            type
                            message
                        }
                        missingEvidence
                        alteredEvidence
                    }
                }
            }
        }
    """

    /**
     * The `auditTrail` field of a build, as a user who can only see it.
     */
    private fun Build.auditTrail(includeEvidence: Boolean? = null): JsonNode {
        var auditTrail: JsonNode? = null
        asUserWithView(this).call {
            run(query, mapOf("id" to id(), "includeEvidence" to includeEvidence)) { data ->
                auditTrail = data.path("build").path("auditTrail")
            }
        }
        return auditTrail!!
    }

    private fun JsonNode.verification(): JsonNode = path("verification")

    /**
     * A build with a story of a few entries: build.created, validation.run, promotion.added.
     */
    private fun trailedBuild(code: Build.() -> Unit) {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        validate(vs)
                        promote(pl)
                        code()
                    }
                }
            }
        }
    }

    private fun Build.entryIds(): Map<Int, Int> = asAdmin { trailService.getEntries(this).associate { it.seq to it.id } }

    @Test
    fun `Entries, endorsements and verification of an untouched trail`() {
        trailedBuild {
            val trail = auditTrail()

            val entries = trail.path("entries")
            assertEquals(
                listOf(TrailEntryTypes.BUILD_CREATED, TrailEntryTypes.VALIDATION_RUN, TrailEntryTypes.PROMOTION_ADDED),
                entries.toList().map { it.path("type").asString() },
            )
            assertEquals(listOf(1, 2, 3), entries.toList().map { it.path("seq").asInt() })
            val first = entries.path(0)
            assertEquals(1, first.path("schemaVersion").asInt())
            assertTrue(first.path("prevHash").isNull, "No previous hash for seq 1")
            assertEquals(id(), first.path("payload").path("build").path("id").asInt())
            assertTrue(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""").matches(first.path("time").asString()), "Time as hashed")
            assertEquals(first.path("hash").asString(), entries.path(1).path("prevHash").asString())

            val key = instanceKeyService.getPublicKeys().single()
            val publicKey = TrailEndorsementFormat.parsePublicKeyPem(key.publicKey)
            val endorsements = trail.path("endorsements")
            assertEquals(listOf(1, 2, 3), endorsements.toList().map { it.path("seq").asInt() })
            assertEquals(entries.toList().map { it.path("id").asInt() }, endorsements.toList().map { it.path("entryId").asInt() })
            endorsements.toList().forEachIndexed { index, endorsement ->
                assertEquals(key.keyId, endorsement.path("keyId").asString())
                assertTrue(
                    TrailEndorsementFormat.verify(
                        publicKey,
                        entries.path(index).path("hash").asString(),
                        endorsement.path("signature").asString(),
                    ),
                    "Endorsement of seq ${index + 1}"
                )
            }

            val verification = trail.verification()
            assertEquals(true, verification.path("chainIntact").asBoolean())
            assertTrue(verification.path("firstBrokenSeq").isNull)
            assertEquals(true, verification.path("endorsementsValid").asBoolean())
            assertTrue(verification.path("firstInvalidEndorsementSeq").isNull)
            assertEquals(false, verification.path("partial").asBoolean())
            assertTrue(verification.path("unendorsedFromSeq").isNull)
            assertEquals(0, verification.path("problems").size())
            assertTrue(verification.path("missingEvidence").isNull, "Evidence not verified by default")
            assertTrue(verification.path("alteredEvidence").isNull, "Evidence not verified by default")
        }
    }

    @Test
    fun `Verification including the evidence`() {
        trailedBuild {
            val verification = auditTrail(includeEvidence = true).verification()
            assertEquals(true, verification.path("chainIntact").asBoolean())
            assertEquals(0, verification.path("missingEvidence").size())
            assertTrue(verification.path("missingEvidence").isArray)
            assertEquals(0, verification.path("alteredEvidence").size())
            assertTrue(verification.path("alteredEvidence").isArray)
        }
    }

    @Test
    fun `A payload tampered with in the database breaks the chain at its entry`() {
        trailedBuild {
            namedParameterJdbcTemplate.update(
                "UPDATE BUILD_TRAIL_ENTRY SET PAYLOAD = :payload WHERE ID = :id",
                mapOf("id" to entryIds().getValue(2), "payload" to """{"status":"PASSED","tampered":true}"""),
            )
            val verification = auditTrail().verification()
            assertEquals(false, verification.path("chainIntact").asBoolean())
            assertEquals(2, verification.path("firstBrokenSeq").asInt())
            assertEquals(
                listOf(2 to "HASH"),
                verification.path("problems").toList().map { it.path("seq").asInt() to it.path("type").asString() },
            )
            assertEquals(true, verification.path("endorsementsValid").asBoolean(), "The stored hash is the endorsed one")
        }
    }

    @Test
    fun `A payload tampered with in the database with its hash recomputed is caught by the endorsements`() {
        trailedBuild {
            val last = asAdmin { trailService.getEntries(this).last() }
            val tamperedPayload = """{"promotionLevel":{"id":0,"name":"PLATINUM"}}""".parseAsJson()
            val tamperedHash = TrailHashFormatV1.hash(last.envelope.copy(payload = tamperedPayload))
            namedParameterJdbcTemplate.update(
                "UPDATE BUILD_TRAIL_ENTRY SET PAYLOAD = :payload, HASH = :hash WHERE ID = :id",
                mapOf("id" to last.id, "payload" to CanonicalJson.canonicalize(tamperedPayload), "hash" to tamperedHash),
            )
            val verification = auditTrail().verification()
            assertEquals(true, verification.path("chainIntact").asBoolean(), "The hashes are consistent")
            assertEquals(false, verification.path("endorsementsValid").asBoolean())
            assertEquals(3, verification.path("firstInvalidEndorsementSeq").asInt())
            assertEquals(
                listOf(3 to "ENDORSEMENT"),
                verification.path("problems").toList().map { it.path("seq").asInt() to it.path("type").asString() },
            )
        }
    }

    @Test
    fun `An entry deleted from the database breaks the chain at its position`() {
        trailedBuild {
            namedParameterJdbcTemplate.update(
                "DELETE FROM BUILD_TRAIL_ENTRY WHERE ID = :id",
                mapOf("id" to entryIds().getValue(2)),
            )
            val verification = auditTrail().verification()
            assertEquals(false, verification.path("chainIntact").asBoolean())
            assertEquals(2, verification.path("firstBrokenSeq").asInt())
        }
    }

    @Test
    fun `Entries whose endorsements were deleted are the unendorsed tail`() {
        trailedBuild {
            namedParameterJdbcTemplate.update(
                "DELETE FROM BUILD_TRAIL_ENDORSEMENT WHERE ENTRY_ID IN (:ids)",
                mapOf("ids" to entryIds().filterKeys { it >= 2 }.values.toList()),
            )
            val trail = auditTrail()
            assertEquals(listOf(1), trail.path("endorsements").toList().map { it.path("seq").asInt() })
            val verification = trail.verification()
            assertEquals(true, verification.path("chainIntact").asBoolean())
            assertEquals(true, verification.path("endorsementsValid").asBoolean())
            assertEquals(2, verification.path("unendorsedFromSeq").asInt())
        }
    }

    @Test
    fun `The trail of a build which predates it is partial, not broken`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    untrailedBuild {
                        promote(pl)
                        val trail = auditTrail()
                        assertEquals(
                            listOf(TrailEntryTypes.TRAIL_OPENED, TrailEntryTypes.PROMOTION_ADDED),
                            trail.path("entries").toList().map { it.path("type").asString() },
                        )
                        val verification = trail.verification()
                        assertEquals(true, verification.path("partial").asBoolean())
                        assertEquals(true, verification.path("chainIntact").asBoolean())
                        assertEquals(true, verification.path("endorsementsValid").asBoolean())
                    }
                }
            }
        }
    }

    @Test
    fun `A build has no trail while the licence is off and no entry was written for it`() {
        asAdmin {
            project {
                branch {
                    untrailedBuild {
                        withoutTrail {
                            assertTrue(auditTrail().isNull, "No trail")
                        }
                        // With the licence on, the trail is there, empty
                        val trail = auditTrail()
                        assertEquals(0, trail.path("entries").size())
                        assertEquals(true, trail.verification().path("chainIntact").asBoolean())
                    }
                }
            }
        }
    }

    @Test
    fun `A trail stays readable and verifiable after the licence lapses`() {
        trailedBuild {
            testLicenseService.withoutFeature(FEATURE_AUDIT_TRAIL) {
                val trail = auditTrail()
                assertEquals(3, trail.path("entries").size())
                assertEquals(3, trail.path("endorsements").size())
                assertEquals(true, trail.verification().path("chainIntact").asBoolean())
                assertEquals(true, trail.verification().path("endorsementsValid").asBoolean())
            }
        }
    }
}
