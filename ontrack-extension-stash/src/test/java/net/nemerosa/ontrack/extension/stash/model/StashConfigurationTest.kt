package net.nemerosa.ontrack.extension.stash.model

import net.nemerosa.ontrack.extension.stash.BitbucketServerFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StashConfigurationTest {

    val configuration = BitbucketServerFixtures.bitbucketServerConfig()

    @Test
    fun obfuscation() {
        assertEquals("", configuration.obfuscate().password)
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with wrong URL`() {
        assertFalse(configuration.matchesUrl("https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with matching HTTP URL`() {
        assertFalse(configuration.matchesUrl("https://bitbucket.dev.nemerosa.com/scm/nemerosa/ontrack.git"))
        assertTrue(configuration.matchesUrl("https://bitbucket.dev.yontrack.com/scm/nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with matching SSH URL`() {
        assertTrue(configuration.matchesUrl("ssh://git@bitbucket.dev.yontrack.com:7999/nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with a SCP-like SSH URL`() {
        assertTrue(configuration.matchesUrl("git@bitbucket.dev.yontrack.com:nemerosa/ontrack.git"))
    }

    @Test
    fun `SCM URL matching is an origin comparison, not a substring test`() {
        // The configured host as a suffix of the actual host
        assertFalse(configuration.matchesUrl("https://bitbucket.dev.yontrack.com.attacker.example/scm/nemerosa/ontrack.git"))
        // The configured host in the path
        assertFalse(configuration.matchesUrl("https://attacker.example/bitbucket.dev.yontrack.com/nemerosa/ontrack.git"))
        // The configured host in the user information
        assertFalse(configuration.matchesUrl("https://bitbucket.dev.yontrack.com@attacker.example/nemerosa/ontrack.git"))
        // The configured host as the SSH user
        assertFalse(configuration.matchesUrl("git@bitbucket.dev.yontrack.com.attacker.example:nemerosa/ontrack.git"))
    }
}
