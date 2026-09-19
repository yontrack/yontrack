package net.nemerosa.ontrack.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BaseExceptionTest {

    private class TestException : BaseException {
        constructor(message: String) : super(message)
        constructor(pattern: String, vararg parameters: Any) : super(pattern, *parameters)
        constructor(ex: Exception, message: String) : super(ex, message)
        constructor(ex: Exception, pattern: String, vararg parameters: Any) : super(ex, pattern, *parameters)
    }

    @Test
    fun `message only is kept verbatim`() {
        assertEquals(
            "Cannot reach /projects/nemerosa%2Fyontrack/repository",
            TestException("Cannot reach /projects/nemerosa%2Fyontrack/repository").message,
        )
    }

    @Test
    fun `message and cause is kept verbatim`() {
        val cause = RuntimeException("Boom")
        val ex = TestException(cause, "Cannot reach /projects/nemerosa%2Fyontrack/repository")
        assertEquals("Cannot reach /projects/nemerosa%2Fyontrack/repository", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `pattern and parameters are still formatted`() {
        assertEquals(
            "Cannot reach nemerosa/yontrack at 10%",
            TestException("Cannot reach %s at 10%%", "nemerosa/yontrack").message,
        )
    }

    @Test
    fun `pattern, parameters and cause are still formatted`() {
        val cause = RuntimeException("Boom")
        val ex = TestException(cause, "Cannot reach %s at 10%%", "nemerosa/yontrack")
        assertEquals("Cannot reach nemerosa/yontrack at 10%", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `empty parameters leave the pattern alone`() {
        assertEquals(
            "Cannot reach /projects/nemerosa%2Fyontrack/repository",
            TestException("Cannot reach /projects/nemerosa%2Fyontrack/repository", *emptyArray<Any>()).message,
        )
    }
}
