package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SignatureRequestTest {

    @Test
    fun `From signature`() {
        val time = TestUtils.dateTime()
        val request = SignatureRequest.of(Signature.of(time, "User"))
        assertEquals("User", request.user)
        assertEquals(time, request.time)
    }

    @Test
    fun `To signature`() {
        val time0 = TestUtils.dateTime()
        val time = time0.plusDays(1)
        val s = SignatureRequest(null, null).getSignature(Signature.of(time, "User"))
        assertEquals("User", s.user.name)
        assertEquals(time, s.time)
    }

    @Test
    fun `To signature with blank user`() {
        val time0 = TestUtils.dateTime()
        val time = time0.plusDays(1)
        val s = SignatureRequest(null, "").getSignature(Signature.of(time, "User"))
        assertEquals("User", s.user.name)
        assertEquals(time, s.time)
    }

    @Test
    fun `To signature with user`() {
        val time0 = TestUtils.dateTime()
        val time = time0.plusDays(1)
        val s = SignatureRequest(null, "Other").getSignature(Signature.of(time, "User"))
        assertEquals("Other", s.user.name)
        assertEquals(time, s.time)
    }

    @Test
    fun `To signature with time`() {
        val time0 = TestUtils.dateTime()
        val time = time0.plusDays(1)
        val s = SignatureRequest(time0, null).getSignature(Signature.of(time, "User"))
        assertEquals("User", s.user.name)
        assertEquals(time0, s.time)
    }

    @Test
    fun `To signature with time and user`() {
        val time0 = TestUtils.dateTime()
        val time = time0.plusDays(1)
        val s = SignatureRequest(time0, "Other").getSignature(Signature.of(time, "User"))
        assertEquals("Other", s.user.name)
        assertEquals(time0, s.time)
    }

}
