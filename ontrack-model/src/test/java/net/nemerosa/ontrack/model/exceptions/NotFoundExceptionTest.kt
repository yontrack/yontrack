package net.nemerosa.ontrack.model.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals

class NotFoundExceptionTest {

    private class TestNotFoundException : NotFoundException {
        constructor(message: String) : super(message)
        constructor(pattern: String, vararg parameters: Any) : super(pattern, *parameters)
    }

    @Test
    fun `a message is kept as it is, format specifiers included`() {
        assertEquals(
            "Branch source with ID %s%n cannot be found.",
            TestNotFoundException("Branch source with ID %s%n cannot be found.").message,
        )
    }

    @Test
    fun `a pattern with parameters is formatted`() {
        assertEquals(
            "Promotion run ID not found: 10",
            TestNotFoundException("Promotion run ID not found: %s", 10).message,
        )
    }
}
