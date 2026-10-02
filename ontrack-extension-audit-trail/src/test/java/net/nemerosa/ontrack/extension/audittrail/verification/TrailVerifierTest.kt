package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.extension.audittrail.endorsement.Endorsement
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKey
import net.nemerosa.ontrack.extension.audittrail.hash.TrailEnvelope
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

/**
 * Verification of a trail from its entries, endorsements and public keys alone — against the
 * shared test vectors of `audit-trail/test-vectors/`, tampered with in every way a row can be.
 */
class TrailVerifierTest {

    private fun resource(path: String): JsonNode =
        TrailVerifierTest::class.java.getResource("/audit-trail/test-vectors/$path")!!
            .readText(Charsets.UTF_8).parseAsJson()

    private val endorsementVector = resource("endorsements/01-rfc8032-test1.json")

    /**
     * The published key of RFC 8032, which endorsed the entries of `01-build-created.json`
     */
    private val rfcKey = InstancePublicKey(
        keyId = endorsementVector.path("keyId").asString(),
        algorithm = endorsementVector.path("algorithm").asString(),
        publicKey = endorsementVector.path("publicKey").asString(),
    )

    private fun JsonNode.toEnvelope() = TrailEnvelope(
        schemaVersion = path("schemaVersion").asInt(),
        seq = path("seq").asInt(),
        type = path("type").asString(),
        time = path("time").asString(),
        actor = path("actor"),
        prevHash = path("prevHash").takeIf { !it.isNull }?.asString(),
        payload = path("payload"),
    )

    /**
     * The three entries of `01-build-created.json`, for build 1042, each endorsed by the key of RFC 8032.
     */
    private val trail: List<TrailVerifier.Entry> by lazy {
        val signatures = endorsementVector.path("endorsements").toList().associate {
            it.path("hash").asString() to it.path("signature").asString()
        }
        resource("01-build-created.json").path("entries").toList().map { entry ->
            val hash = entry.path("hash").asString()
            TrailVerifier.Entry(
                envelope = entry.path("envelope").toEnvelope(),
                hash = hash,
                endorsements = listOf(Endorsement(keyId = rfcKey.keyId, signature = signatures.getValue(hash))),
            )
        }
    }

    private fun verify(
        entries: List<TrailVerifier.Entry> = trail,
        keys: List<InstancePublicKey> = listOf(rfcKey),
        buildId: Int? = 1042,
    ) = TrailVerifier.verify(entries = entries, keys = keys, buildId = buildId)

    @Test
    fun `An untouched trail is intact and validly endorsed`() {
        val verification = verify()
        assertEquals(true, verification.chainIntact)
        assertEquals(null, verification.firstBrokenSeq)
        assertEquals(true, verification.endorsementsValid)
        assertEquals(null, verification.firstInvalidEndorsementSeq)
        assertEquals(false, verification.partial)
        assertEquals(null, verification.unendorsedFromSeq)
        assertEquals(emptyList(), verification.problems)
    }

    /**
     * Replaces the entry at the given position.
     */
    private fun List<TrailVerifier.Entry>.tamper(
        position: Int,
        code: TrailVerifier.Entry.() -> TrailVerifier.Entry,
    ): List<TrailVerifier.Entry> = mapIndexed { index, entry -> if (index + 1 == position) entry.code() else entry }

    private fun TrailVerifier.Entry.withPayload(json: String) = copy(envelope = envelope.copy(payload = json.parseAsJson()))

    @Test
    fun `A tampered payload breaks the chain at its entry`() {
        val verification = verify(
            trail.tamper(2) {
                withPayload("""{"qualifier":"","target":{"branch":"main","id":978,"name":"1.18.0","project":"ledger-core"}}""")
            }
        )
        assertEquals(false, verification.chainIntact)
        assertEquals(2, verification.firstBrokenSeq)
        assertEquals(
            listOf(2 to TrailVerificationProblemType.HASH),
            verification.problems.map { it.seq to it.type },
        )
        assertEquals(true, verification.endorsementsValid, "The stored hash is still the endorsed one")
    }

    @Test
    fun `A tampered hash breaks the chain at its entry and at the link of the next one, and invalidates its endorsement`() {
        val verification = verify(
            trail.tamper(2) { copy(hash = "0".repeat(64)) }
        )
        assertEquals(false, verification.chainIntact)
        assertEquals(2, verification.firstBrokenSeq)
        assertEquals(
            listOf(
                2 to TrailVerificationProblemType.HASH,
                2 to TrailVerificationProblemType.ENDORSEMENT,
                3 to TrailVerificationProblemType.PREVIOUS_HASH,
            ),
            verification.problems.map { it.seq to it.type },
        )
        assertEquals(false, verification.endorsementsValid)
        assertEquals(2, verification.firstInvalidEndorsementSeq)
    }

    @Test
    fun `A tampered actor, type or time breaks the chain at its entry`() {
        listOf<TrailVerifier.Entry.() -> TrailVerifier.Entry>(
            { copy(envelope = envelope.copy(actor = """{"account":"alice","via":"ui"}""".parseAsJson())) },
            { copy(envelope = envelope.copy(type = "link.removed")) },
            { copy(envelope = envelope.copy(time = "2026-10-02T08:15:31.251Z")) },
        ).forEach { change ->
            val verification = verify(trail.tamper(2, change))
            assertEquals(2, verification.firstBrokenSeq)
        }
    }

    @Test
    fun `An entry outside the subset of canonical JSON breaks the chain at its entry`() {
        val verification = verify(
            trail.tamper(3) {
                withPayload("""{"dataSha256":null,"dataType":null,"runId":55120.5,"status":"PASSED","validationStamp":"unit-tests"}""")
            }
        )
        assertEquals(
            listOf(3 to TrailVerificationProblemType.HASH),
            verification.problems.map { it.seq to it.type },
        )
    }
    @Test
    fun `A trail rewritten with recomputed hashes keeps an intact chain but loses its endorsements`() {
        val rewritten2 = trail[1].withPayload("""{"qualifier":"","target":{"branch":"main","id":978,"name":"1.18.0","project":"ledger-core"}}""")
            .let { it.copy(hash = TrailHashFormatV1.hash(it.envelope)) }
        val rewritten3 = trail[2].copy(envelope = trail[2].envelope.copy(prevHash = rewritten2.hash))
            .let { it.copy(hash = TrailHashFormatV1.hash(it.envelope)) }
        val verification = verify(listOf(trail[0], rewritten2, rewritten3))
        assertEquals(true, verification.chainIntact)
        assertEquals(false, verification.endorsementsValid)
        assertEquals(2, verification.firstInvalidEndorsementSeq)
        assertEquals(
            listOf(2 to TrailVerificationProblemType.ENDORSEMENT, 3 to TrailVerificationProblemType.ENDORSEMENT),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `Reordered entries break the chain at the first misplaced one`() {
        val verification = verify(listOf(trail[0], trail[2], trail[1]))
        assertEquals(false, verification.chainIntact)
        assertEquals(2, verification.firstBrokenSeq)
        assertEquals(
            listOf(
                2 to TrailVerificationProblemType.SEQ,
                2 to TrailVerificationProblemType.PREVIOUS_HASH,
                3 to TrailVerificationProblemType.SEQ,
                3 to TrailVerificationProblemType.PREVIOUS_HASH,
            ),
            verification.problems.map { it.seq to it.type },
        )
        assertEquals(true, verification.endorsementsValid, "Each entry is still endorsed")
    }

    @Test
    fun `A removed entry breaks the chain at its position`() {
        val verification = verify(listOf(trail[0], trail[2]))
        assertEquals(2, verification.firstBrokenSeq)
        assertEquals(
            listOf(2 to TrailVerificationProblemType.SEQ, 2 to TrailVerificationProblemType.PREVIOUS_HASH),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `A trail whose first entry was removed is broken at seq 1`() {
        val verification = verify(listOf(trail[1], trail[2]))
        assertEquals(1, verification.firstBrokenSeq)
        assertEquals(
            listOf(
                1 to TrailVerificationProblemType.SEQ,
                1 to TrailVerificationProblemType.PREVIOUS_HASH,
                1 to TrailVerificationProblemType.FIRST_ENTRY,
                1 to TrailVerificationProblemType.BUILD,
                2 to TrailVerificationProblemType.SEQ,
            ),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `The trail of another build is broken at seq 1`() {
        val verification = verify(buildId = 1043)
        assertEquals(1, verification.firstBrokenSeq)
        assertEquals(
            listOf(1 to TrailVerificationProblemType.BUILD),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `The build is not checked when it is not known`() {
        assertEquals(true, verify(buildId = null).chainIntact)
    }

    @Test
    fun `An entry of an unsupported schema version breaks the chain`() {
        val verification = verify(trail.tamper(3) { copy(envelope = envelope.copy(schemaVersion = 2)) })
        assertEquals(
            listOf(3 to TrailVerificationProblemType.SCHEMA_VERSION),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `An endorsement which is not the signature of the hash of its entry is invalid`() {
        val verification = verify(trail.tamper(3) { copy(endorsements = trail[0].endorsements) })
        assertEquals(true, verification.chainIntact)
        assertEquals(false, verification.endorsementsValid)
        assertEquals(3, verification.firstInvalidEndorsementSeq)
        assertEquals(
            listOf(3 to TrailVerificationProblemType.ENDORSEMENT),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `An endorsement which is not base64 is invalid`() {
        val verification = verify(trail.tamper(1) { copy(endorsements = listOf(Endorsement(rfcKey.keyId, "not base64!"))) })
        assertEquals(1, verification.firstInvalidEndorsementSeq)
    }

    @Test
    fun `An endorsement by an unknown key is invalid`() {
        val verification = verify(keys = emptyList())
        assertEquals(true, verification.chainIntact)
        assertEquals(false, verification.endorsementsValid)
        assertEquals(1, verification.firstInvalidEndorsementSeq)
        assertEquals(
            listOf(1, 2, 3).map { it to TrailVerificationProblemType.UNKNOWN_KEY },
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `A key published under another ID is not trusted`() {
        val verification = verify(keys = listOf(rfcKey.copy(keyId = "0123456789abcdef")))
        assertEquals(TrailVerificationProblemType.UNKNOWN_KEY, verification.problems.first().type)
    }

    @Test
    fun `The entries after the last endorsed one are the unendorsed tail`() {
        val verification = verify(trail.tamper(2) { copy(endorsements = emptyList()) }.tamper(3) { copy(endorsements = emptyList()) })
        assertEquals(true, verification.chainIntact)
        assertEquals(true, verification.endorsementsValid, "A missing endorsement is not an invalid one")
        assertEquals(2, verification.unendorsedFromSeq)
    }

    @Test
    fun `An unendorsed entry followed by an endorsed one is covered through the chain`() {
        val verification = verify(trail.tamper(1) { copy(endorsements = emptyList()) }.tamper(2) { copy(endorsements = emptyList()) })
        assertEquals(null, verification.unendorsedFromSeq)
        assertEquals(true, verification.endorsementsValid)
    }

    @Test
    fun `A trail with no endorsement at all is unendorsed from seq 1`() {
        val verification = verify(trail.map { it.copy(endorsements = emptyList()) })
        assertEquals(1, verification.unendorsedFromSeq)
        assertEquals(true, verification.chainIntact)
    }

    @Test
    fun `A trail opened with trail-opened is partial, not broken`() {
        val key = InstanceKey.parse(endorsementVector.path("privateKey").asString().toByteArray(Charsets.US_ASCII))
        val partialTrail = resource("02-trail-opened.json").path("entries").toList().map { entry ->
            val hash = entry.path("hash").asString()
            TrailVerifier.Entry(
                envelope = entry.path("envelope").toEnvelope(),
                hash = hash,
                endorsements = listOf(Endorsement(keyId = key.keyId, signature = key.endorse(hash))),
            )
        }
        val verification = verify(partialTrail)
        assertEquals(true, verification.partial)
        assertEquals(true, verification.chainIntact)
        assertEquals(true, verification.endorsementsValid)
        assertEquals(emptyList(), verification.problems)
    }

    @Test
    fun `An empty trail is intact`() {
        val verification = verify(entries = emptyList())
        assertEquals(true, verification.chainIntact)
        assertEquals(true, verification.endorsementsValid)
        assertEquals(false, verification.partial)
        assertEquals(null, verification.unendorsedFromSeq)
    }
}
