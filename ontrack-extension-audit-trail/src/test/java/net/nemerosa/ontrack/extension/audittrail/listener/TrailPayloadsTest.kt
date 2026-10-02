package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class TrailPayloadsTest {

    @Test
    fun `A payload leaves the values which are not set out`() {
        val payload = TrailPayloads.payload(
            "name" to "1.0.0",
            "description" to null,
            "runTime" to 42,
            "old" to mapOf("name" to "0.9", "description" to null),
        )
        assertEquals("""{"name":"1.0.0","old":{"name":"0.9"},"runTime":42}""", CanonicalJson.canonicalize(payload))
    }

    @Test
    fun `What a signature claims is its time at the millisecond in UTC, and its user`() {
        val claimed = TrailPayloads.claimed(
            Signature.of(LocalDateTime.of(2026, 9, 30, 14, 5, 7, 123_456_789), "jenkins")
        )
        assertEquals(mapOf("time" to "2026-09-30T14:05:07.123Z", "user" to "jenkins"), claimed)
    }

    @Test
    fun `The numbers canonical JSON rejects are written as strings, the others kept`() {
        val value = TrailPayloads.canonicalValue(
            """{"coverage":87.5,"count":3,"issues":[1,9007199254740992],"max":-9007199254740991,"ok":true,"none":null}""".parseAsJson()
        )
        assertEquals(
            """{"count":3,"coverage":"87.5","issues":[1,"9007199254740992"],"max":-9007199254740991,"none":null,"ok":true}""",
            CanonicalJson.canonicalize(value)
        )
    }

    @Test
    fun `Data is referred to by the SHA-256 of its canonical form`() {
        val sha256 = TrailPayloads.sha256(
            """{"name":"x","issues":[1,9007199254740992],"coverage":87.5}""".parseAsJson()
        )
        // shasum -a 256 of {"coverage":"87.5","issues":[1,"9007199254740992"],"name":"x"}
        assertEquals("c2b47ecea99c180aa2705e54658b853d386e3976f311a1083eb730413ddff3bc", sha256)
    }
}
