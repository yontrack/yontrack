package net.nemerosa.ontrack.extension.audittrail.hash

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hash format v1, against the shared test vectors of `audit-trail/test-vectors/`, which were
 * produced independently of this code and which the CLI verifies too.
 */
class TrailHashFormatV1Test {

    private val vectors: List<Pair<String, JsonNode>> by lazy {
        val dir = File(TrailHashFormatV1Test::class.java.getResource("/audit-trail/test-vectors")!!.toURI())
        dir.listFiles { file -> file.name.endsWith(".json") }!!
            .sortedBy { it.name }
            .map { it.name to it.readText(Charsets.UTF_8).parseAsJson() }
    }

    private fun JsonNode.toEnvelope() = TrailEnvelope(
        schemaVersion = path("schemaVersion").asInt(),
        seq = path("seq").asInt(),
        type = path("type").asString(),
        time = path("time").asString(),
        actor = path("actor"),
        prevHash = path("prevHash").takeIf { !it.isNull }?.asString(),
        payload = path("payload"),
    )

    @Test
    fun `The shared test vectors are present`() {
        assertTrue(vectors.size >= 3, "At least three test vector files")
    }

    @Test
    fun `Canonical bytes of every envelope of the test vectors`() {
        vectors.forEach { (name, vector) ->
            vector.path("entries").forEach { entry ->
                assertEquals(
                    entry.path("canonical").asString(),
                    TrailHashFormatV1.canonical(entry.path("envelope").toEnvelope()),
                    "Canonical form of seq ${entry.path("envelope").path("seq")} in $name"
                )
            }
        }
    }

    @Test
    fun `Hash of every entry of the test vectors`() {
        vectors.forEach { (name, vector) ->
            vector.path("entries").forEach { entry ->
                assertEquals(
                    entry.path("hash").asString(),
                    TrailHashFormatV1.hash(entry.path("envelope").toEnvelope()),
                    "Hash of seq ${entry.path("envelope").path("seq")} in $name"
                )
            }
        }
    }

    @Test
    fun `Every entry of the test vectors is chained to the one before it`() {
        vectors.forEach { (name, vector) ->
            var previous: String? = null
            vector.path("entries").forEachIndexed { index, entry ->
                val envelope = entry.path("envelope").toEnvelope()
                assertEquals(index + 1, envelope.seq, "Seq in $name")
                assertEquals(previous, envelope.prevHash, "Previous hash of seq ${envelope.seq} in $name")
                previous = TrailHashFormatV1.hash(envelope)
            }
        }
    }

    @Test
    fun `The hash changes when any field of the envelope changes`() {
        val envelope = TrailEnvelope(
            schemaVersion = 1,
            seq = 2,
            type = "promotion.added",
            time = "2026-10-02T09:00:00.123Z",
            actor = mapOf("account" to "alice", "via" to "ui").asJson(),
            prevHash = "4b320b3f1628780eaf5adbc9153975aa05eeb25fd082f97b111588499a6550a6",
            payload = mapOf("promotionLevel" to "GOLD").asJson(),
        )
        val hash = TrailHashFormatV1.hash(envelope)
        listOf(
            envelope.copy(seq = 3),
            envelope.copy(type = "promotion.removed"),
            envelope.copy(time = "2026-10-02T09:00:00.124Z"),
            envelope.copy(actor = mapOf("account" to "bob", "via" to "ui").asJson()),
            envelope.copy(prevHash = "8a78e74944dfb4d80786ff009c402384a693b357769811fecc156ba78eecf7d5"),
            envelope.copy(payload = mapOf("promotionLevel" to "SILVER").asJson()),
        ).forEach {
            assertTrue(TrailHashFormatV1.hash(it) != hash, "Hash changes for $it")
        }
    }

    @Test
    fun `The hash is 64 lowercase hexadecimal characters`() {
        val hash = TrailHashFormatV1.hash(
            TrailEnvelope(
                schemaVersion = 1,
                seq = 1,
                type = "build.created",
                time = "2026-10-02T08:15:30.000Z",
                actor = mapOf("account" to "alice", "via" to "ui").asJson(),
                prevHash = null,
                payload = mapOf("build" to mapOf("id" to 1)).asJson(),
            )
        )
        assertTrue(Regex("[0-9a-f]{64}").matches(hash), "Lowercase hex SHA-256: $hash")
    }

    @Test
    fun `Only the envelopes of schema version 1 are hashed by the format v1`() {
        assertThrows<IllegalArgumentException> {
            TrailHashFormatV1.hash(
                TrailEnvelope(
                    schemaVersion = 2,
                    seq = 1,
                    type = "build.created",
                    time = "2026-10-02T08:15:30.000Z",
                    actor = mapOf("account" to "alice").asJson(),
                    prevHash = null,
                    payload = mapOf("build" to mapOf("id" to 1)).asJson(),
                )
            )
        }
    }

    @Test
    fun `Time in ISO-8601 UTC with exactly three digits of milliseconds`() {
        assertEquals("2026-10-02T08:15:30.000Z", TrailHashFormatV1.formatTime(LocalDateTime.of(2026, 10, 2, 8, 15, 30)))
        assertEquals(
            "2026-10-02T08:15:30.120Z",
            TrailHashFormatV1.formatTime(LocalDateTime.of(2026, 10, 2, 8, 15, 30, 120_000_000))
        )
    }

    @Test
    fun `Time beyond the millisecond is truncated, never rounded`() {
        assertEquals(
            "2026-10-02T08:15:30.999Z",
            TrailHashFormatV1.formatTime(LocalDateTime.of(2026, 10, 2, 8, 15, 30, 999_999_999))
        )
    }
}
