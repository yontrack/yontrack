package net.nemerosa.ontrack.extension.gitlab.scm

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.GitLabIssueServiceExtension
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClient
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject
import net.nemerosa.ontrack.extension.gitlab.model.GitLabUser
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.gitlab.settings.GitLabSettings
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The auto-versioning merge request, against a mocked [GitLabClient]: what is sent when the merge request is
 * created, and what each of the two approval modes does afterwards.
 *
 * The polling interval is set to a millisecond here - the point being which calls happen and in what order,
 * not how long they take.
 */
class GitLabSCMExtensionPullRequestTest {

    private val configuration = GitLabConfiguration(
        name = "gl",
        url = "https://gitlab.com",
        token = "secret",
    )

    private val client = mockk<GitLabClient>(relaxUnitFun = true)

    private val configurationService = mockk<GitLabConfigurationService>()

    private val cachedSettingsService = mockk<CachedSettingsService>()

    private val extension = GitLabSCMExtension(
        extensionFeature = mockk<GitLabExtensionFeature>(relaxed = true),
        propertyService = mockk<PropertyService>(),
        structureService = mockk<StructureService>(),
        clientFactory = mockk<GitLabClientFactory>().apply {
            every { create(any()) } returns client
        },
        cachedSettingsService = cachedSettingsService,
        configurationService = configurationService,
        issueServiceRegistry = mockk<IssueServiceRegistry>(),
        issueServiceExtension = mockk<GitLabIssueServiceExtension>(),
        gitRepositoryClientFactory = mockk<GitRepositoryClientFactory>(),
        gitConfigService = mockk(),
    )

    private fun settings(
        squash: Boolean = true,
        removeSourceBranch: Boolean = true,
        autoMergeTimeout: Long = 5_000,
    ) {
        every { cachedSettingsService.getCachedSettings(GitLabSettings::class.java) } returns GitLabSettings(
            squash = squash,
            removeSourceBranch = removeSourceBranch,
            autoMergeTimeout = autoMergeTimeout,
            autoMergeInterval = 1,
        )
    }

    private fun scm(): SCM {
        every { configurationService.findConfiguration("gl") } returns configuration
        every { client.getProject(any()) } answers {
            val path = firstArg<String>()
            if (path == PROJECT) GitLabProject(path_with_namespace = path, default_branch = "main") else null
        }
        return assertNotNull(extension.getSCMPath("gl", "$PROJECT/ontrack.yaml")?.scm)
    }

    private fun mergeRequest(
        iid: Long = 12,
        state: String = "opened",
        detailedMergeStatus: String? = "mergeable",
        sha: String = "abcdef",
        squashOnMerge: Boolean = true,
    ) = GitLabMergeRequest(
        id = iid * 100,
        iid = iid,
        title = "Some title",
        state = state,
        source_branch = "feature/one",
        target_branch = "main",
        web_url = "https://gitlab.com/$PROJECT/-/merge_requests/$iid",
        sha = sha,
        detailed_merge_status = detailedMergeStatus,
        squash_on_merge = squashOnMerge,
    )

    private fun onCreate(mr: GitLabMergeRequest = mergeRequest()) {
        every {
            client.createMergeRequest(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mr
    }

    private fun createPR(
        scm: SCM,
        autoApproval: Boolean = false,
        remoteAutoMerge: Boolean = false,
        reviewers: List<String> = emptyList(),
    ) = scm.createPR(
        from = "feature/one",
        to = "main",
        title = "Some title",
        description = "Some description",
        autoApproval = autoApproval,
        remoteAutoMerge = remoteAutoMerge,
        message = "Some message",
        reviewers = reviewers,
    )

    @Test
    fun `A merge request is created with the settings of the instance`() {
        settings(squash = false, removeSourceBranch = false)
        onCreate()
        val pr = createPR(scm())
        verify(exactly = 1) {
            client.createMergeRequest(
                project = PROJECT,
                sourceBranch = "feature/one",
                targetBranch = "main",
                title = "Some title",
                description = "Some description",
                reviewerIds = emptyList(),
                removeSourceBranch = false,
                squash = false,
            )
        }
        assertEquals("PR-12", pr.name)
        assertEquals("12", pr.id)
        assertEquals("https://gitlab.com/$PROJECT/-/merge_requests/12", pr.link)
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
    }

    @Test
    fun `Reviewers are resolved into their numeric ids`() {
        settings()
        onCreate()
        every { client.findUserByUsername("alice") } returns GitLabUser(id = 42, username = "alice")
        every { client.findUserByUsername("bob") } returns GitLabUser(id = 43, username = "bob")
        createPR(scm(), reviewers = listOf("alice", "bob"))
        verify(exactly = 1) {
            client.createMergeRequest(
                project = PROJECT,
                sourceBranch = any(),
                targetBranch = any(),
                title = any(),
                description = any(),
                reviewerIds = listOf(42L, 43L),
                removeSourceBranch = any(),
                squash = any(),
            )
        }
    }

    /**
     * A reviewer GitLab does not know must not cost the version change: the merge request is created
     * without them.
     */
    @Test
    fun `An unknown reviewer is skipped rather than failing the order`() {
        settings()
        onCreate()
        every { client.findUserByUsername("ghost") } returns null
        every { client.findUserByUsername("alice") } returns GitLabUser(id = 42, username = "alice")
        createPR(scm(), reviewers = listOf("ghost", "alice"))
        verify(exactly = 1) {
            client.createMergeRequest(
                project = PROJECT,
                sourceBranch = any(),
                targetBranch = any(),
                title = any(),
                description = any(),
                reviewerIds = listOf(42L),
                removeSourceBranch = any(),
                squash = any(),
            )
        }
    }

    @Test
    fun `Without auto approval nothing is approved and nothing is merged`() {
        settings()
        onCreate()
        createPR(scm())
        verify(exactly = 0) { client.approveMergeRequest(any(), any()) }
        verify(exactly = 0) { client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Client mode approves, waits for the merge request to be mergeable, and merges`() {
        settings()
        onCreate(mergeRequest(detailedMergeStatus = "ci_still_running"))
        // The merge request becomes mergeable on the second poll
        every { client.getMergeRequest(PROJECT, 12) } returnsMany listOf(
            mergeRequest(detailedMergeStatus = "ci_still_running"),
            mergeRequest(detailedMergeStatus = "mergeable"),
        )
        every {
            client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any())
        } returns mergeRequest(state = "merged")
        val pr = createPR(scm(), autoApproval = true)
        verify(exactly = 1) { client.approveMergeRequest(PROJECT, 12) }
        verify(exactly = 1) {
            client.mergeMergeRequest(
                project = PROJECT,
                iid = 12,
                sha = "abcdef",
                message = "Some message",
                squash = true,
                removeSourceBranch = true,
                autoMerge = false,
            )
        }
        assertEquals(SCMPullRequestStatus.MERGED, pr.status)
    }

    /**
     * The `sha` is always sent: GitLab 19.2 can make it mandatory, and a mismatch is a 409 rather than a
     * merge of something else.
     */
    @Test
    fun `The sha of the merge request is always sent on merge`() {
        settings()
        onCreate(mergeRequest(sha = "1234567890"))
        every {
            client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any())
        } returns mergeRequest(state = "merged")
        createPR(scm(), autoApproval = true)
        val sha = slot<String>()
        verify(exactly = 1) {
            client.mergeMergeRequest(
                project = PROJECT,
                iid = 12,
                sha = capture(sha),
                message = any(),
                squash = any(),
                removeSourceBranch = any(),
                autoMerge = any(),
            )
        }
        assertEquals("1234567890", sha.captured)
    }

    /**
     * `squash_on_merge` is what GitLab will actually do; a project can force it either way, whatever the
     * Yontrack setting asked for at creation time.
     */
    @Test
    fun `Squashing is read back from the merge request rather than repeated from the settings`() {
        listOf(false to true, true to false).forEach { (setting, squashOnMerge) ->
            settings(squash = setting)
            onCreate(mergeRequest(squashOnMerge = squashOnMerge))
            every {
                client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any())
            } returns mergeRequest(state = "merged")
            createPR(scm(), autoApproval = true)
            verify(exactly = 1) {
                client.mergeMergeRequest(
                    project = PROJECT,
                    iid = 12,
                    sha = any(),
                    message = any(),
                    squash = squashOnMerge,
                    removeSourceBranch = any(),
                    autoMerge = any(),
                )
            }
        }
    }

    @Test
    fun `SCM mode approves and hands the merge back to GitLab with auto merge`() {
        settings()
        onCreate(mergeRequest(detailedMergeStatus = "ci_still_running"))
        every {
            client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any())
        } returns mergeRequest(state = "opened")
        val pr = createPR(scm(), autoApproval = true, remoteAutoMerge = true)
        verify(exactly = 1) { client.approveMergeRequest(PROJECT, 12) }
        verify(exactly = 1) {
            client.mergeMergeRequest(
                project = PROJECT,
                iid = 12,
                sha = "abcdef",
                message = "Some message",
                squash = true,
                removeSourceBranch = true,
                autoMerge = true,
            )
        }
        // Nothing is polled: GitLab does the waiting
        verify(exactly = 0) { client.getMergeRequest(any(), any()) }
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
    }

    /**
     * A conflict never resolves itself, so waiting out the whole timeout would only delay the report.
     */
    @Test
    fun `A merge request which cannot be merged is given up on at once`() {
        settings(autoMergeTimeout = 600_000)
        onCreate(mergeRequest(detailedMergeStatus = "conflict"))
        every { client.getMergeRequest(PROJECT, 12) } returns mergeRequest(detailedMergeStatus = "conflict")
        val pr = createPR(scm(), autoApproval = true)
        verify(exactly = 0) { client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
    }

    @Test
    fun `A merge request which never becomes mergeable times out and is not merged`() {
        settings(autoMergeTimeout = 50)
        onCreate(mergeRequest(detailedMergeStatus = "ci_still_running"))
        every { client.getMergeRequest(PROJECT, 12) } returns mergeRequest(detailedMergeStatus = "ci_still_running")
        val pr = createPR(scm(), autoApproval = true)
        verify(exactly = 0) { client.mergeMergeRequest(any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
    }

    @Test
    fun `A merge request GitLab returns without a sha cannot be merged`() {
        settings()
        onCreate(mergeRequest().copy(sha = null))
        every { client.getMergeRequest(PROJECT, 12) } returns mergeRequest().copy(sha = null)
        val scm = scm()
        assertThrows<GitLabSCMNoMergeRequestShaException> {
            createPR(scm, autoApproval = true)
        }
    }

    companion object {
        private const val PROJECT = "group/project"
    }
}
