package net.nemerosa.ontrack.extension.gitlab.scm

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.GitLabIssueServiceExtension
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClient
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The parts of the GitLab SCM which need no Yontrack instance: the reference parser, which has to cope with
 * project paths of any depth, and the mapping of a merge request onto a pull request.
 */
class GitLabSCMExtensionTest {

    private val configuration = GitLabConfiguration(
        name = "gl",
        url = "https://gitlab.com",
        token = "secret",
    )

    private val client = mockk<GitLabClient>()

    private val configurationService = mockk<GitLabConfigurationService>()

    private val extension = GitLabSCMExtension(
        extensionFeature = mockk<GitLabExtensionFeature>(relaxed = true),
        propertyService = mockk<PropertyService>(),
        structureService = mockk<StructureService>(),
        clientFactory = mockk<GitLabClientFactory>().apply {
            every { create(any()) } returns client
        },
        cachedSettingsService = mockk<CachedSettingsService>(),
        configurationService = configurationService,
        issueServiceRegistry = mockk<IssueServiceRegistry>(),
        issueServiceExtension = mockk<GitLabIssueServiceExtension>(),
        gitRepositoryClientFactory = mockk<GitRepositoryClientFactory>(),
        gitConfigService = mockk(),
    )

    /**
     * Only the listed paths are GitLab projects; anything else answers 404, as the API would.
     */
    private fun projects(vararg paths: String) {
        every { configurationService.findConfiguration("gl") } returns configuration
        every { client.getProject(any()) } answers {
            val path = firstArg<String>()
            if (path in paths) GitLabProject(path_with_namespace = path, default_branch = "main") else null
        }
    }

    @Test
    fun `A reference to a project directly under a group`() {
        projects("group/project")
        val scmPath = extension.getSCMPath("gl", "group/project/ontrack.yaml")
        assertEquals("ontrack.yaml", scmPath?.path)
        assertEquals("group/project", scmPath?.scm?.repository)
    }

    @Test
    fun `A reference to a project in a subgroup`() {
        projects("group/subgroup/project")
        val scmPath = extension.getSCMPath("gl", "group/subgroup/project/ontrack.yaml")
        assertEquals("ontrack.yaml", scmPath?.path)
        assertEquals("group/subgroup/project", scmPath?.scm?.repository)
    }

    @Test
    fun `A reference to a project nested several subgroups deep`() {
        projects("group/a/b/c/project")
        val scmPath = extension.getSCMPath("gl", "group/a/b/c/project/config/ontrack.yaml")
        assertEquals("config/ontrack.yaml", scmPath?.path)
        assertEquals("group/a/b/c/project", scmPath?.scm?.repository)
    }

    @Test
    fun `A file path several directories deep is not mistaken for a subgroup`() {
        // The trap: `group/project/src/main/app.yaml` has the very same shape as a project three subgroups
        // deep. Only GitLab can tell them apart, and it says `group/project` is the project.
        projects("group/project")
        val scmPath = extension.getSCMPath("gl", "group/project/src/main/app.yaml")
        assertEquals("src/main/app.yaml", scmPath?.path)
        assertEquals("group/project", scmPath?.scm?.repository)
    }

    @Test
    fun `A deep project path wins over the shallow prefix that is only a group`() {
        // `group/subgroup` is a group, not a project, so it is not a candidate however early it is tried.
        projects("group/subgroup/project")
        val scmPath = extension.getSCMPath("gl", "group/subgroup/project/src/app.yaml")
        assertEquals("src/app.yaml", scmPath?.path)
        assertEquals("group/subgroup/project", scmPath?.scm?.repository)
    }

    @Test
    fun `A reference naming no known project is refused`() {
        projects()
        assertThrows<GitLabSCMProjectNotFoundException> {
            extension.getSCMPath("gl", "group/project/ontrack.yaml")
        }
    }

    @Test
    fun `A reference with no room for both a project and a file is refused`() {
        projects("group/project")
        listOf("group/project", "group", "ontrack.yaml").forEach { ref ->
            assertThrows<GitLabSCMRefParsingException>("Refused: $ref") {
                extension.getSCMPath("gl", ref)
            }
        }
    }

    @Test
    fun `A reference on an unknown configuration is null`() {
        every { configurationService.findConfiguration("nope") } returns null
        assertNull(extension.getSCMPath("nope", "group/project/ontrack.yaml"))
    }

    @Test
    fun `The URLs of the SCM of a reference`() {
        projects("group/subgroup/project")
        val scm = extension.getSCMPath("gl", "group/subgroup/project/ontrack.yaml")?.scm
        assertEquals("gitlab", scm?.engine)
        assertEquals("git", scm?.type)
        assertEquals("https://gitlab.com/group/subgroup/project.git", scm?.repositoryURI)
        assertEquals("https://gitlab.com/group/subgroup/project", scm?.repositoryHtmlURL)
        assertEquals(
            "https://gitlab.com/group/subgroup/project/-/compare/aaa...bbb",
            scm?.getDiffLink("aaa", "bbb"),
        )
    }

    @Test
    fun `A merge request is a pull request named by its iid`() {
        projects("group/project")
        every { client.getMergeRequest("group/project", 12) } returns GitLabMergeRequest(
            id = 1200,
            iid = 12,
            title = "Some merge request",
            state = "merged",
            source_branch = "feature/one",
            target_branch = "main",
            web_url = "https://gitlab.com/group/project/-/merge_requests/12",
        )
        val scm = extension.getSCMPath("gl", "group/project/ontrack.yaml")?.scm
        val pr = scm?.getPullRequestByName("PR-12")
        assertEquals("12", pr?.id)
        assertEquals("PR-12", pr?.name)
        assertEquals("https://gitlab.com/group/project/-/merge_requests/12", pr?.link)
        assertEquals(SCMPullRequestStatus.MERGED, pr?.status)
    }

    /**
     * `#12` is an **issue** reference on GitLab - the very form this module's issue service parses - so it
     * is not a merge request name, and is not accepted as one.
     */
    @Test
    fun `A name which is not a merge request reference is null`() {
        projects("group/project")
        val scm = extension.getSCMPath("gl", "group/project/ontrack.yaml")?.scm
        assertNull(scm?.getPullRequestByName("#12"))
        assertNull(scm?.getPullRequestByName("!12"))
        assertNull(scm?.getPullRequestByName("PR-not-a-number"))
    }
}
