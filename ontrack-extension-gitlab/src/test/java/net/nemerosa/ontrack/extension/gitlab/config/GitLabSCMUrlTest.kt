package net.nemerosa.ontrack.extension.gitlab.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitLabSCMUrlTest {

    @Test
    fun `HTTPS URL on gitlab_com`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Subgroups are part of the project path`() {
        assertEquals(
            "nemerosa/tools/ci/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/nemerosa/tools/ci/yontrack.git")
        )
    }

    @Test
    fun `HTTPS URL with a user`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://oauth2@gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `SCP-like SSH URL`() {
        assertEquals(
            "nemerosa/tools/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", "git@gitlab.com:nemerosa/tools/yontrack.git")
        )
    }

    @Test
    fun `SSH URL with a port`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.example.com", "ssh://git@gitlab.example.com:2222/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `No git suffix`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/nemerosa/yontrack")
        )
    }

    @Test
    fun `Trailing slash`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/nemerosa/yontrack.git/")
        )
    }

    @Test
    fun `Configuration URL with a trailing slash`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com/", "https://gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Self-managed instance`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.dev.yontrack.com", "https://gitlab.dev.yontrack.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Self-managed instance, the port of the URLs does not have to match`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.dev.yontrack.com:8443", "git@gitlab.dev.yontrack.com:nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Host comparison is not case sensitive`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://GitLab.com", "https://gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Self-managed instance under a relative URL root`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://dev.yontrack.com/gitlab", "https://dev.yontrack.com/gitlab/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `A relative URL root is required on the HTTPS form`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://dev.yontrack.com/gitlab", "https://dev.yontrack.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `A relative URL root is optional on the SSH form`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://dev.yontrack.com/gitlab", "git@dev.yontrack.com:nemerosa/yontrack.git")
        )
    }

    @Test
    fun `Another host altogether`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://github.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `A host having the configured host as a suffix does not match`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com.attacker.example/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `A host having the configured host as a prefix does not match`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.common.example/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `The configured host in the path does not match`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://attacker.example/gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `The configured host in the user information does not match`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com@attacker.example/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `The configured host as the SSH user does not match`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "git@gitlab.com.attacker.example:nemerosa/yontrack.git")
        )
    }

    @Test
    fun `A project must live in a namespace`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/yontrack.git")
        )
    }

    @Test
    fun `The instance URL itself is not a project`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/")
        )
    }

    @Test
    fun `Empty path segments are rejected`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "https://gitlab.com/nemerosa//yontrack.git")
        )
    }

    @Test
    fun `A configuration URL without a host matches nothing`() {
        assertNull(
            GitLabSCMUrl.projectPath("gitlab.com", "https://gitlab.com/nemerosa/yontrack.git")
        )
    }

    @Test
    fun `A SCM URL which is not an URL at all matches nothing`() {
        assertNull(
            GitLabSCMUrl.projectPath("https://gitlab.com", "not an URL")
        )
    }
}
