package net.nemerosa.ontrack.extension.stash.scm

import net.nemerosa.ontrack.extension.stash.client.BitbucketServerPRMergeRejectedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeWhenMergeableTest {

    private fun rejected() = BitbucketServerPRMergeRejectedException(
        prId = 1,
        reason = "New changes were pushed to the target branch. Please retry the merge.",
    )

    @Test
    fun `Merged at once when the PR is mergeable`() {
        var merges = 0
        val merged = mergeWhenMergeable(
            timeoutMillis = 1_000,
            intervalMillis = 1,
            isMergeable = { true },
            merge = { merges++ },
        )
        assertTrue(merged)
        assertEquals(1, merges)
    }

    @Test
    fun `Merged once the PR becomes mergeable`() {
        var checks = 0
        var merges = 0
        val merged = mergeWhenMergeable(
            timeoutMillis = 1_000,
            intervalMillis = 1,
            isMergeable = { ++checks >= 3 },
            merge = { merges++ },
        )
        assertTrue(merged)
        assertEquals(3, checks)
        assertEquals(1, merges)
    }

    @Test
    fun `Merge retried when rejected because the target branch moved`() {
        var checks = 0
        var attempts = 0
        val retries = mutableListOf<BitbucketServerPRMergeRejectedException>()
        val merged = mergeWhenMergeable(
            timeoutMillis = 1_000,
            intervalMillis = 1,
            isMergeable = { checks++; true },
            merge = {
                attempts++
                if (attempts < 3) throw rejected()
            },
            onRejected = { retries += it },
        )
        assertTrue(merged)
        assertEquals(3, attempts)
        assertEquals(3, checks, "The PR is checked again before each new attempt")
        assertEquals(2, retries.size)
    }

    @Test
    fun `Not merged when the PR never becomes mergeable`() {
        var merges = 0
        val merged = mergeWhenMergeable(
            timeoutMillis = 50,
            intervalMillis = 5,
            isMergeable = { false },
            merge = { merges++ },
        )
        assertFalse(merged)
        assertEquals(0, merges)
    }

    @Test
    fun `Not merged when the merge keeps being rejected until the timeout`() {
        val merged = mergeWhenMergeable(
            timeoutMillis = 50,
            intervalMillis = 5,
            isMergeable = { true },
            merge = { throw rejected() },
        )
        assertFalse(merged)
    }

    @Test
    fun `Other merge errors are not retried`() {
        var attempts = 0
        assertThrows<IllegalStateException> {
            mergeWhenMergeable(
                timeoutMillis = 1_000,
                intervalMillis = 1,
                isMergeable = { true },
                merge = {
                    attempts++
                    error("Server error")
                },
            )
        }
        assertEquals(1, attempts)
    }
}
