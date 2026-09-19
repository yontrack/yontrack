package net.nemerosa.ontrack.model.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals

class InputExceptionTest {

    private class TestInputException(
        pattern: String,
        vararg parameters: Any,
    ) : InputException(pattern, *parameters)

    private class TestNotFoundException(
        pattern: String,
        vararg parameters: Any,
    ) : NotFoundException(pattern, *parameters)

    @Test
    fun `an already interpolated message carrying a percent is not formatted`() {
        assertEquals(
            "CI config extension not found: nemerosa%2Fyontrack.",
            TestInputException("CI config extension not found: nemerosa%2Fyontrack.").message,
        )
    }

    @Test
    fun `parameters are still formatted`() {
        assertEquals(
            "CI config extension not found: gitlab.",
            TestInputException("CI config extension not found: %s.", "gitlab").message,
        )
    }

    @Test
    fun `not found - an already interpolated message carrying a percent is not formatted`() {
        assertEquals(
            "Branch not found: feature%2Fsomething.",
            TestNotFoundException("Branch not found: feature%2Fsomething.").message,
        )
    }

    @Test
    fun `not found - parameters are still formatted`() {
        assertEquals(
            "Branch not found: main.",
            TestNotFoundException("Branch not found: %s.", "main").message,
        )
    }
}
