package net.nemerosa.ontrack.model.tx

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.CannotCreateTransactionException
import java.sql.SQLTransientConnectionException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TransactionRetryTest {

    private fun retry(
        retries: Int = 3,
        retryDelay: Duration = Duration.ZERO,
    ): TransactionRetry {
        val properties = OntrackConfigProperties().apply {
            tx.retries = retries
            tx.retryDelay = retryDelay
            tx.retryMaxDelay = retryDelay
        }
        return TransactionRetry(properties)
    }

    /**
     * The failure Ontrack actually saw in production: the pool could not hand out a connection.
     */
    private fun poolExhausted() = CannotCreateTransactionException(
        "Could not open JDBC Connection for transaction",
        SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 30000ms"),
    )

    @Test
    fun `Success on the first attempt runs the code only once`() {
        var attempts = 0
        val result = retry().withRetry {
            attempts++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `Transient failure is retried until it succeeds`() {
        var attempts = 0
        val result = retry().withRetry {
            attempts++
            if (attempts < 3) throw poolExhausted()
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `Transient failure is rethrown when the retries are exhausted`() {
        var attempts = 0
        val last = poolExhausted()
        val thrown = assertFailsWith<CannotCreateTransactionException> {
            retry(retries = 2).withRetry {
                attempts++
                throw last
            }
        }
        assertSame(last, thrown, "The last failure is given back to the caller")
        // 1 initial attempt + 2 retries
        assertEquals(3, attempts)
    }

    @Test
    fun `A business failure is never retried`() {
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            retry().withRetry {
                attempts++
                error("Business failure")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `Transient failures are recognised anywhere in the cause chain`() {
        var attempts = 0
        val result = retry().withRetry {
            attempts++
            if (attempts < 2) {
                throw IllegalStateException("Wrapped", poolExhausted())
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `Spring transient data access exceptions are retried`() {
        var attempts = 0
        val result = retry().withRetry {
            attempts++
            if (attempts < 2) throw QueryTimeoutException("Query timed out")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `Retries can be disabled`() {
        var attempts = 0
        assertFailsWith<CannotCreateTransactionException> {
            retry(retries = 0).withRetry {
                attempts++
                throw poolExhausted()
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `A cause cycle does not hang the transient detection`() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second", first)
        first.initCause(second)
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            retry().withRetry {
                attempts++
                throw second
            }
        }
        assertEquals(1, attempts)
    }
}
