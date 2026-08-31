package net.nemerosa.ontrack.kdsl.acceptance.tests.github

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestOnGitHubConditionTest {

    @Test
    fun `enabled when both the organization and the token are set`() {
        assertTrue(TestOnGitHubCondition.isEnabled("nemerosa", "ghp_xxx"))
    }

    /**
     * The point of the whole condition: without a token the tests reach github.com anonymously
     * and are rate-limited per runner IP, which turned unrelated builds red.
     */
    @Test
    fun `disabled when the token is missing`() {
        assertFalse(TestOnGitHubCondition.isEnabled("nemerosa", null))
    }

    @Test
    fun `disabled when the token is blank`() {
        assertFalse(TestOnGitHubCondition.isEnabled("nemerosa", "   "))
    }

    @Test
    fun `disabled when the organization is missing`() {
        assertFalse(TestOnGitHubCondition.isEnabled(null, "ghp_xxx"))
    }

    @Test
    fun `disabled when the organization is blank`() {
        assertFalse(TestOnGitHubCondition.isEnabled("", "ghp_xxx"))
    }

    @Test
    fun `disabled when nothing is set`() {
        assertFalse(TestOnGitHubCondition.isEnabled(null, null))
    }
}
