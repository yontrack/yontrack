package net.nemerosa.ontrack.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UserExceptionTest {

    private class TestUserException(
        message: String,
        exception: Exception? = null,
    ) : UserException(message, exception)

    @Test
    fun `an interpolated message carrying a percent is not reformatted`() {
        assertEquals(
            "401 Unauthorized on /api/v4/projects/nemerosa%2Fyontrack/repository/branches",
            TestUserException("401 Unauthorized on /api/v4/projects/nemerosa%2Fyontrack/repository/branches").message,
        )
    }

    @Test
    fun `an interpolated message carrying a percent is not reformatted when wrapping a cause`() {
        val cause = RuntimeException("401 Unauthorized on /api/v4/projects/nemerosa%2Fyontrack/repository/branches")
        val ex = TestUserException("Unexpected error: ${cause.message}", cause)
        assertEquals(
            "Unexpected error: 401 Unauthorized on /api/v4/projects/nemerosa%2Fyontrack/repository/branches",
            ex.message,
        )
        assertSame(cause, ex.cause)
    }

    @Test
    fun `a bare percent at the end of the message is kept`() {
        assertEquals("Disk is at 100%", TestUserException("Disk is at 100%").message)
    }
}
