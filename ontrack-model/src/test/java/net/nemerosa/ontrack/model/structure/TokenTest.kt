package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.*

class TokenTest {

    @Test
    fun json() {
        val token = Token(
            "default",
            "xxx",
            LocalDateTime.of(2019, 4, 25, 18, 22, 0),
            LocalDateTime.of(2019, 5, 25, 18, 22, 0),
            null,
        )
        assertFalse(token.valid, "Invalid token, in the past")
        assertEquals(
            mapOf(
                "name" to "default",
                "value" to "xxx",
                "creation" to "2019-04-25T18:22:00Z",
                "validUntil" to "2019-05-25T18:22:00Z",
                "lastUsed" to null,
                "readOnly" to false,
                "valid" to false
            ).asJson(),
            token.asJson()
        )
    }

    @Test
    fun obfuscation() {
        val token = Token(
            "default",
            "xxx",
            Time.now(),
            Time.now() + Duration.ofDays(14),
            null,
        )
        val ob = token.obfuscate()
        assertEquals("", ob.value)
        assertEquals(token.creation, ob.creation)
        assertEquals(token.validUntil, ob.validUntil)
    }

    @Test
    fun `Always valid when valid until is null`() {
        val now = Time.now()
        val token = Token("default", "x", now, null, null)
        assertTrue(token.isValid(), "Valid now")
        assertTrue(token.isValid(now + Duration.ofDays(3650)), "Valid in 10 years")
    }

    @Test
    fun `Limited validity`() {
        val now = Time.now()
        val token = Token("default", "x", now, now + Duration.ofDays(30), null)
        assertTrue(token.isValid(), "Valid now")
        assertTrue(token.isValid(now + Duration.ofDays(29)), "Valid in 29 days")
        assertFalse(token.isValid(Time.now() + Duration.ofDays(31)), "Not valid in 31 days")
    }

    @Test
    fun `Token valid with null value`() {
        val token = Token("default", "x", Time.now(), null, null).validFor(null)
        assertNull(token.validUntil, "Always valid")
    }

    @Test
    fun `Token valid with negative value`() {
        val token = Token("default", "x", Time.now(), null, null).validFor(Duration.ofDays(-1))
        assertNull(token.validUntil, "Always valid")
    }

    @Test
    fun `Token valid with zero value`() {
        val token = Token("default", "x", Time.now(), null, null).validFor(Duration.ofDays(0))
        assertNull(token.validUntil, "Always valid")
    }

    @Test
    fun `Token valid with value`() {
        val now = Time.now()
        val token = Token("default", "x", now, null, null).validFor(Duration.ofDays(1))
        assertNotNull(token.validUntil, "Validity") {
            val duration = Duration.between(now, it)
            assertEquals(1, duration.toDays())
        }
    }

}