package net.nemerosa.ontrack.extension.scm.support

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SCMUrlTest {

    @Test
    fun `HTTPS clone URL on the configured host`() {
        assertTrue(SCMUrl.sameHost("https://github.com", "https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `SCP-like SSH clone URL on the configured host`() {
        assertTrue(SCMUrl.sameHost("https://github.com", "git@github.com:nemerosa/yontrack.git"))
    }

    @Test
    fun `The scheme does not have to match`() {
        assertTrue(SCMUrl.sameHost("http://bitbucket.dev.yontrack.com", "https://bitbucket.dev.yontrack.com/scm/nemerosa/yontrack.git"))
    }

    @Test
    fun `The port does not have to match`() {
        assertTrue(
            SCMUrl.sameHost(
                "https://bitbucket.dev.yontrack.com",
                "ssh://git@bitbucket.dev.yontrack.com:7999/nemerosa/yontrack.git"
            )
        )
    }

    @Test
    fun `The path of the configuration URL is ignored`() {
        assertTrue(SCMUrl.sameHost("https://bitbucket.dev.yontrack.com/", "https://bitbucket.dev.yontrack.com/scm/nemerosa/yontrack.git"))
    }

    @Test
    fun `Host comparison is not case sensitive`() {
        assertTrue(SCMUrl.sameHost("https://GitHub.com", "https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `Another host altogether`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "https://bitbucket.dev.yontrack.com/scm/nemerosa/yontrack.git"))
    }

    @Test
    fun `A host having the configured host as a suffix does not match`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "https://github.com.attacker.example/nemerosa/yontrack.git"))
    }

    @Test
    fun `A host having the configured host as a prefix does not match`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "https://github.common.example/nemerosa/yontrack.git"))
    }

    @Test
    fun `A subdomain of the configured host does not match`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "https://github.dev.yontrack.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `The configured host in the path does not match`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "https://attacker.example/github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `The configured host in the user information does not match`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "https://github.com@attacker.example/nemerosa/yontrack.git"))
    }

    @Test
    fun `The configured host as the SSH user does not match`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "git@github.com.attacker.example:nemerosa/yontrack.git"))
    }

    @Test
    fun `A host name with an underscore is compared like any other`() {
        assertTrue(SCMUrl.sameHost("https://git_server.internal", "https://git_server.internal/nemerosa/yontrack.git"))
        assertTrue(SCMUrl.sameHost("https://git_server.internal", "ssh://git@git_server.internal:7999/nemerosa/yontrack.git"))
        assertFalse(SCMUrl.sameHost("https://git_server.internal", "https://git_server.internal.attacker.example/nemerosa/yontrack.git"))
        assertFalse(SCMUrl.sameHost("https://git_server.internal", "https://git_server.internal@attacker.example/nemerosa/yontrack.git"))
    }

    @Test
    fun `A configuration URL without a host matches nothing`() {
        assertFalse(SCMUrl.sameHost("github.com", "https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `A blank configuration URL matches nothing`() {
        assertFalse(SCMUrl.sameHost("", "https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `A SCM URL which is not an URL at all matches nothing`() {
        assertFalse(SCMUrl.sameHost("https://github.com", "not an URL"))
    }

    @Test
    fun `Parsing of a HTTPS URL`() {
        val parsed = SCMUrl.parseScmUrl("https://oauth2@gitlab.com/nemerosa/tools/yontrack.git")
        assertEquals("gitlab.com", parsed?.host)
        assertEquals("nemerosa/tools/yontrack.git", parsed?.path)
        assertEquals(false, parsed?.scp)
    }

    @Test
    fun `Parsing of a SCP-like SSH URL`() {
        val parsed = SCMUrl.parseScmUrl("git@gitlab.com:nemerosa/tools/yontrack.git")
        assertEquals("gitlab.com", parsed?.host)
        assertEquals("nemerosa/tools/yontrack.git", parsed?.path)
        assertEquals(true, parsed?.scp)
    }

    @Test
    fun `Parsing of a configuration URL with a relative root`() {
        val parsed = SCMUrl.parseConfigurationUrl("https://dev.yontrack.com/gitlab/")
        assertEquals("dev.yontrack.com", parsed?.host)
        assertEquals("gitlab", parsed?.path)
    }

    @Test
    fun `A configuration URL is never a SCP-like URL`() {
        assertNull(SCMUrl.parseConfigurationUrl("git@gitlab.com:nemerosa/yontrack.git"))
    }
}
