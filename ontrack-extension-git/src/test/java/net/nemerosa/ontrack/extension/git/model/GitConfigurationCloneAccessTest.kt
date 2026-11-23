package net.nemerosa.ontrack.extension.git.model

import net.nemerosa.ontrack.git.AnonymousGitRepositoryAuthenticator
import net.nemerosa.ontrack.git.AppTokenGitRepositoryAuthenticator
import net.nemerosa.ontrack.git.TokenGitRepositoryAuthenticator
import net.nemerosa.ontrack.git.UsernamePasswordGitRepositoryAuthenticator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GitConfigurationCloneAccessTest {

    @Test
    fun `Anonymous authenticator has no credentials`() {
        assertNull(AnonymousGitRepositoryAuthenticator.INSTANCE.toSCMCloneCredentials())
    }

    @Test
    fun `Username and password are kept`() {
        val credentials = assertNotNull(
            UsernamePasswordGitRepositoryAuthenticator("user", "secret").toSCMCloneCredentials()
        )
        assertEquals("user", credentials.username)
        assertEquals("secret", credentials.password)
    }

    @Test
    fun `A token is a password under the token username`() {
        val credentials = assertNotNull(TokenGitRepositoryAuthenticator("tkn").toSCMCloneCredentials())
        assertEquals("x-access-token", credentials.username)
        assertEquals("tkn", credentials.password)
    }

    @Test
    fun `An application token is fetched when the credentials are asked for`() {
        var calls = 0
        val authenticator = AppTokenGitRepositoryAuthenticator { calls++; "app-$calls" }
        assertEquals(0, calls)
        val credentials = assertNotNull(authenticator.toSCMCloneCredentials())
        assertEquals("x-access-token", credentials.username)
        assertEquals("app-1", credentials.password)
        assertEquals("app-2", authenticator.toSCMCloneCredentials()?.password)
    }

    @Test
    fun `The password is never printed`() {
        val credentials = assertNotNull(TokenGitRepositoryAuthenticator("tkn").toSCMCloneCredentials())
        assertFalse("tkn" in credentials.toString())
    }

}
