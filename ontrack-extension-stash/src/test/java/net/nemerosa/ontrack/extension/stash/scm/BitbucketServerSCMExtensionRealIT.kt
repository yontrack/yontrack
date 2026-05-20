package net.nemerosa.ontrack.extension.stash.scm

import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.extension.stash.AbstractBitbucketTestSupport
import net.nemerosa.ontrack.extension.stash.TestOnBitbucketServer
import net.nemerosa.ontrack.extension.stash.bitbucketServerEnv
import net.nemerosa.ontrack.extension.stash.client.BitbucketClientFactory
import net.nemerosa.ontrack.extension.stash.model.BitbucketRepository
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.files.FileRefService
import net.nemerosa.ontrack.model.files.downloadDocument
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

@TestOnBitbucketServer
@AsAdminTest
class BitbucketServerSCMExtensionRealIT : AbstractBitbucketTestSupport() {

    @Autowired
    private lateinit var extension: BitbucketServerSCMExtension

    @Autowired
    private lateinit var scmRefService: FileRefService

    @Autowired
    private lateinit var scmDetector: SCMDetector

    @Autowired
    private lateinit var bitbucketClientFactory: BitbucketClientFactory

    @Test
    fun `Using a complete SCM reference to download a file`() {
        val config = createBitbucketServerConfig()
        val document = scmRefService.downloadDocument(
            "scm://bitbucket-server/${config.name}/${bitbucketServerEnv.project}/${bitbucketServerEnv.repository}/${bitbucketServerEnv.path}",
            "text/plain"
        ) ?: fail("Cannot download test document")
        val content = document.content.decodeToString()
        assertEquals(bitbucketServerEnv.pathContent.trim(), content.trim())
    }

    @Test
    fun `Given a configuration, gets a SCM Path`() {
        val config = createBitbucketServerConfig()
        val (scm, path) = extension.getSCMPath(
            config.name,
            "${bitbucketServerEnv.project}/${bitbucketServerEnv.repository}/${bitbucketServerEnv.path}"
        ) ?: fail("Cannot get SCM")
        val document = scm.download(bitbucketServerEnv.defaultBranch, bitbucketServerEnv.path)
            ?: fail("Cannot download test document")
        val content = document.decodeToString()
        assertEquals(bitbucketServerEnv.pathContent.trim(), content.trim())
    }

    @Test
    fun `Project not configured does not have a BB Server SCM`() {
        project {
            val scm = scmDetector.getSCM(this)
            assertNull(scm, "No SCM for project")
        }
    }

    @Test
    fun `Project configured has a BB Server SCM`() {
        project {
            bitbucketServerConfig()
            val scm = scmDetector.getSCM(this)
            assertNotNull(scm, "SCM for project") {
                assertEquals(
                    "${bitbucketServerEnv.url}/projects/${bitbucketServerEnv.project}/repos/${bitbucketServerEnv.repository}",
                    it.repositoryHtmlURL
                )
            }
        }
    }

    @Test
    fun `Creating a branch`() {
        withScm { scm, _, _ ->
            val branchName = uid("branch/")
            val commit = scm.createBranch(bitbucketServerEnv.defaultBranch, branchName)
            assertTrue(commit.isNotBlank(), "Commit returned")
        }
    }

    @Test
    fun `Creating a pull request with reviewers`() {
        withScm { scm, _, _ ->
            // Creating the branch
            val branchName = uid("branch/")
            scm.createBranch(bitbucketServerEnv.defaultBranch, branchName)
            // Uploading a file to this branch
            val path = uid("file_") + ".txt"
            scm.upload(
                scmBranch = branchName,
                commit = "",
                path = path,
                content = "Some text content".encodeToByteArray(),
                message = "Test for PR"
            )
            // Creating a PR
            val pr = scm.createPR(
                from = branchName,
                to = "main",
                title = uid("pr_"),
                description = "Test PR with reviewers",
                autoApproval = false,
                remoteAutoMerge = false,
                message = "Test PR with reviewers",
                reviewers = listOf(
                    bitbucketServerEnv.autoMergeUser ?: error("Auto merge user is required")
                ),
            )
            // TODO Checks that the PR has some reviewers
        }
    }

    @Test
    fun `Editing and downloading a file on a branch`() {
        withScm { scm, _, _ ->
            val branchName = uid("branch/")
            val fileName = uid("file_")
            val fileContent = uid("content_")
            scm.createBranch(bitbucketServerEnv.defaultBranch, branchName)
            // Creating the file
            scm.upload(
                scmBranch = branchName,
                commit = "",
                path = fileName,
                content = fileContent.encodeToByteArray(),
                message = "This is a test"
            )
            // Downloading the file
            val content = scm.download(
                scmBranch = branchName,
                path = fileName,
            )
            // Checks the content
            assertNotNull(content, "File content") {
                assertEquals(fileContent, it.decodeToString())
            }
        }
    }

    @Test
    fun `Creating a PR without auto approval`() {
        withPR(autoApproval = false) { _, pr, _, _ ->
            assertEquals(SCMPullRequestStatus.OPEN, pr.status, "PR not merged")
        }
    }

    @Test
    fun `Creating a PR with auto approval`() {
        withPR(autoApproval = true) { _, pr, head, config ->
            assertEquals(SCMPullRequestStatus.MERGED, pr.status, "PR is merged")
            // Checks that the source branch has been deleted
            val client = bitbucketClientFactory.getBitbucketClient(config)
            val repo = BitbucketRepository(
                project = bitbucketServerEnv.project,
                repository = bitbucketServerEnv.repository,
            )
            assertFalse(
                client.isBranchExisting(repo, head),
                "PR source branch has been deleted: $head"
            )
        }
    }

    @Test
    fun `Creating and deleting a branch`() {
        withScm { scm, _, _ ->
            val branch = uid("test/")
            // Creating the branch
            val commit = scm.createBranch(
                sourceBranch = bitbucketServerEnv.defaultBranch,
                newBranch = branch
            )
            assertTrue(commit.isNotBlank(), "Commit returned")
            // Checking the branch has been created
            assertNotNull(scm.getBranchLastCommit(branch)) {
                assertEquals(commit, it)
            }
            // Deleting the branch
            scm.deleteBranch(branch)
            // Checking the branch has been deleted
            assertNull(scm.getBranchLastCommit(branch), "Branch has been deleted")

        }
    }

    @Test
    fun `Creating and deleting a branch with a PR`() {
        withPR(autoApproval = false, remoteAutoMerge = false) { scm, _, head, _ ->
            // Deleting the branch
            scm.deleteBranch(head)
            // Checking the branch has been deleted
            assertNull(scm.getBranchLastCommit(head), "Branch has been deleted")

        }
    }

    @Test
    fun `Listing all commits`() {
        withScm { scm, _, project ->
            if (scm is SCMChangeLogEnabled) {
                val commits = mutableListOf<SCMCommit>()
                scm.forAllCommits(project) {
                    commits += it
                }
                assertTrue(commits.isNotEmpty(), "At least one commit must be present")
            } else {
                fail("SCM should be SCMChangeLogEnabled")
            }
        }
    }

    private fun withPR(
        autoApproval: Boolean,
        remoteAutoMerge: Boolean = false,
        code: (scm: SCM, pr: SCMPullRequest, head: String, config: StashConfiguration) -> Unit,
    ) {
        withScm { scm, config, _ ->
            val commonName = uid("branch-")
            val baseName = "base/$commonName"
            val headName = "head/$commonName"
            val fileName = uid("file_")
            val fileContent = uid("content_")
            val newFileContent = uid("content_new_")
            // Base branch
            val baseCommit = scm.createBranch(bitbucketServerEnv.defaultBranch, baseName)
            // Initial file on the base
            scm.upload(
                scmBranch = baseName,
                commit = "",
                path = fileName,
                content = fileContent.encodeToByteArray(),
                message = "Initial file",
            )
            // Creating the head branch
            val headCommit = scm.createBranch(baseName, headName)
            // Updating the file on this branch
            scm.upload(
                scmBranch = headName,
                commit = headCommit,
                path = fileName,
                content = newFileContent.encodeToByteArray(),
                message = "Changed file",
            )
            // Creating the PR without auto merge & approval
            val pr = scm.createPR(
                from = headName,
                to = baseName,
                title = "Sample pull request",
                description = "Sample pull request description",
                autoApproval = autoApproval,
                remoteAutoMerge = remoteAutoMerge,
                message = "Commit message for auto merge",
                reviewers = emptyList(),
            )
            // Checks
            assertTrue(pr.id.isNotBlank(), "PR created")
            code(scm, pr, headName, config)
        }
    }

    private fun withScm(code: (scm: SCM, config: StashConfiguration, project: Project) -> Unit) {
        project {
            val config = bitbucketServerConfig()
            val scm = scmDetector.getSCM(this) ?: fail("No SCM for project")
            code(scm, config, this)
        }
    }

}