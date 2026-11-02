package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.common.Time
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenReadOnlyTest {

    private val token = Token("name", "value", Time.now(), null, null, readOnly = true)

    @Test
    fun `A token is full by default`() {
        assertFalse(Token("name", "value", Time.now(), null, null).readOnly)
        assertFalse(TokenOptions("name").readOnly)
    }

    @Test
    fun `Every copy of a token keeps its read-only attribute`() {
        assertTrue(token.obfuscate().readOnly, "obfuscate")
        assertTrue(token.validFor(null).readOnly, "validFor, no validity")
        assertTrue(token.validFor(Duration.ofDays(1)).readOnly, "validFor, a validity")
        assertTrue(token.withLastUsed(Time.now()).readOnly, "withLastUsed")
    }

}
