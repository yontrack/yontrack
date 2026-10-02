package net.nemerosa.ontrack.extension.audittrail.hash

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import tools.jackson.databind.node.JsonNodeFactory
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.HexFormat

/**
 * Hash format v1 of the entries of a trail — schema version `1`.
 *
 * **This format is a compatibility contract.** Every entry ever written with schema version 1 is
 * verified with it, by Yontrack and offline by `yontrack audit-trail verify`, for as long as such
 * entries exist. Nothing in it may change: any change to what is hashed, or how, is a new schema
 * version with a format of its own, and verification keeps supporting this one.
 *
 * ## Hashed bytes
 *
 * The hash of an entry is the SHA-256 digest, written as 64 lowercase hexadecimal characters, of
 * the UTF-8 bytes of the RFC 8785 canonical form ([CanonicalJson]) of its *envelope*, the JSON
 * object:
 *
 * ```
 * {
 *   "schemaVersion": 1,
 *   "seq": <seq>,
 *   "type": "<type>",
 *   "time": "<time>",
 *   "actor": <actor>,
 *   "prevHash": "<hash of the entry seq - 1>" | null,
 *   "payload": <payload>
 * }
 * ```
 *
 * - `schemaVersion` — the integer `1`;
 * - `seq` — the position of the entry in the trail of its build, from `1`, without gap;
 * - `type` — the type of the entry, such as `build.created` or `trail.opened`;
 * - `time` — the server time of the entry, ISO-8601 in UTC with exactly three digits of
 *   milliseconds and a `Z`: `2026-10-02T08:15:30.120Z` ([formatTime]);
 * - `actor` — a JSON object identifying who made the change;
 * - `prevHash` — the hash of the entry `seq - 1`, `null` for `seq` 1;
 * - `payload` — a JSON object describing the change.
 *
 * `actor` and `payload` are restricted to the subset of JSON that [CanonicalJson] accepts: no
 * decimal number, no integer beyond ±(2⁵³ − 1), no lone surrogate.
 *
 * Its canonical form sorts the properties of the envelope, which gives, for example:
 *
 * ```
 * {"actor":{"account":"alice","via":"ui"},"payload":{...},"prevHash":null,"schemaVersion":1,"seq":1,"time":"2026-10-02T08:15:30.120Z","type":"build.created"}
 * ```
 *
 * ## Test vectors
 *
 * The JSON files of `ontrack-extension-audit-trail/src/test/resources/audit-trail/test-vectors/` hold
 * the reference vectors of this format, shared with the tests of the CLI. Each file is one trail:
 * `{description, schemaVersion, entries: [{envelope, canonical, hash}]}`, where `envelope` is the
 * input, `canonical` the canonical form whose UTF-8 bytes are hashed, and `hash` the expected hash.
 * The entries of a file are chained, each `prevHash` being the `hash` of the entry before it.
 */
object TrailHashFormatV1 {

    /**
     * The schema version this format hashes.
     */
    const val SCHEMA_VERSION = 1

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")

    /**
     * Writes a UTC time as the `time` of an envelope: ISO-8601, exactly three digits of milliseconds,
     * `Z`. Anything beyond the millisecond is truncated.
     *
     * @param time Time, in UTC
     * @return Time as hashed
     */
    fun formatTime(time: LocalDateTime): String = time.truncatedTo(ChronoUnit.MILLIS).format(TIME_FORMAT)

    /**
     * Canonical form of an envelope: the text whose UTF-8 bytes are hashed.
     *
     * @param envelope Envelope of schema version 1
     * @return Canonical JSON text
     */
    fun canonical(envelope: TrailEnvelope): String {
        require(envelope.schemaVersion == SCHEMA_VERSION) {
            "Hash format v1 only hashes the entries of schema version $SCHEMA_VERSION, not ${envelope.schemaVersion}."
        }
        val node = JsonNodeFactory.instance.objectNode().apply {
            put("schemaVersion", envelope.schemaVersion)
            put("seq", envelope.seq)
            put("type", envelope.type)
            put("time", envelope.time)
            set("actor", envelope.actor)
            if (envelope.prevHash != null) {
                put("prevHash", envelope.prevHash)
            } else {
                putNull("prevHash")
            }
            set("payload", envelope.payload)
        }
        return CanonicalJson.canonicalize(node)
    }

    /**
     * Hash of an envelope.
     *
     * @param envelope Envelope of schema version 1
     * @return SHA-256 of its canonical form, 64 lowercase hexadecimal characters
     */
    fun hash(envelope: TrailEnvelope): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(canonical(envelope).toByteArray(Charsets.UTF_8))
        )
}
