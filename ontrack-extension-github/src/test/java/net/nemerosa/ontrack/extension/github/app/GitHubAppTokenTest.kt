package net.nemerosa.ontrack.extension.github.app

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppAccount
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppInstallation
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubAppTokenTest {

    @Test
    fun `Valid before the date`() {
        val now = Time.now()
        val token = GitHubAppToken("xxx", createdAt = now, validUntil = now.plusMinutes(1), installation = installation())
        assertTrue(token.isValid(), "Token is still valid")
    }

    @Test
    fun `Invalid before the date when invalidated`() {
        val now = Time.now()
        val token = GitHubAppToken("xxx", createdAt = now, validUntil = now.plusMinutes(1), installation = installation())
        assertTrue(token.isValid(), "Token is still valid")
        token.invalidate()
        assertFalse(token.isValid(), "Token is no longer valid")
    }

    @Test
    fun `Invalid after the date`() {
        val now = Time.now()
        val token = GitHubAppToken("xxx", createdAt = now, validUntil = now.minusMinutes(1), installation = installation())
        assertFalse(token.isValid(), "Token is no longer valid")
    }

    private fun installation() = GitHubAppInstallation("1234567890", GitHubAppAccount("test", ""))

}