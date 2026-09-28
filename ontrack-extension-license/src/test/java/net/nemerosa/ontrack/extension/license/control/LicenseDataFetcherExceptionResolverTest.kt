package net.nemerosa.ontrack.extension.license.control

import graphql.schema.DataFetchingEnvironmentImpl
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LicenseDataFetcherExceptionResolverTest {

    private val resolver = LicenseDataFetcherExceptionResolver()
    private val env = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build()

    @Test
    fun `A feature not allowed by the licence is a forbidden error naming the feature`() {
        val errors = resolver.resolveException(LicenseFeatureException("extension.test"), env).block()!!
        assertEquals(1, errors.size)
        assertEquals(ErrorType.FORBIDDEN, errors.single().errorType)
        assertEquals("Feature not allowed by the license: extension.test", errors.single().message)
    }

    @Test
    fun `A licence limit is a forbidden error with its message`() {
        val errors = resolver.resolveException(LicenseFeatureDataException("extension.test", "Too many"), env).block()!!
        assertEquals(ErrorType.FORBIDDEN, errors.single().errorType)
        assertEquals("License issue for extension.test: Too many", errors.single().message)
    }

    @Test
    fun `Other exceptions are left to the other resolvers`() {
        val errors = resolver.resolveException(IllegalStateException("Other"), env).block()
        assertTrue(errors == null)
    }
}
