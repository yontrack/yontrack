package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.bitbucket.cloud.*
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudConfigurator
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudGitConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationProperty
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.files.FileRefService
import net.nemerosa.ontrack.model.files.downloadDocument
import net.nemerosa.ontrack.model.structure.Project
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.client.RestTemplateBuilder
import kotlin.test.*

/**
 * SCM against the real Bitbucket Cloud test workspace. Every branch is uniquely named and deleted at the end;
 * whatever a failed run leaves behind is removed by [BitbucketCloudTestCleanup].
 *
 * The fixture repository has custom pipelines only, so none of the pushes below starts a pipeline.
 */
@AsAdminTest
class BitbucketCloudSCMExtensionRealIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var scmDetector: SCMDetector

    @Autowired
    private lateinit var fileRefService: FileRefService

    @Autowired
    private lateinit var bitbucketCloudConfigurator: BitbucketCloudConfigurator

    private val names = BitbucketCloudTestNames()

    private val env: BitbucketCloudTestEnv get() = bitbucketCloudTestEnv

    @TestOnBitbucketCloud
    fun `Project with the Bitbucket Cloud property has a Bitbucket Cloud SCM`() {
        withScm { scm, _, _ ->
            assertEquals("bitbucket-cloud", scm.engine)
            assertEquals("${env.workspace}/${env.repository}", scm.repository)
            assertEquals("https://bitbucket.org/${env.workspace}/${env.repository}", scm.repositoryHtmlURL)
        }
    }

    @TestOnBitbucketCloud
    fun `Downloading a file using a complete SCM reference`() {
        val config = createConfig(bitbucketCloudTestConfigReal())
        val document = fileRefService.downloadDocument(
            "scm://bitbucket-cloud/${config.name}/${env.workspace}/${env.repository}/${BitbucketCloudTestFixture.VERSION_FILE}",
            "text/plain"
        ) ?: fail("Cannot download the fixture file")
        assertContains(document.content.decodeToString(), "${BitbucketCloudTestFixture.VERSION_PROPERTY}=")
    }

    @TestOnBitbucketCloud
    fun `Downloading a file with an access token`() {
        val config = createConfig(bitbucketCloudTestConfigRealAccessToken())
        val (scm, path) = extensionPath(config, BitbucketCloudTestFixture.VERSION_FILE)
        val content = scm.download(BitbucketCloudTestFixture.MAIN_BRANCH, path)
            ?: fail("Cannot download the fixture file")
        assertContains(content.decodeToString(), "${BitbucketCloudTestFixture.VERSION_PROPERTY}=")
    }

    @TestOnBitbucketCloud
    fun `Creating and deleting a branch`() {
        withScm { scm, _, _ ->
            val branch = names.branch("scm-branch")
            val commit = scm.createBranch(BitbucketCloudTestFixture.MAIN_BRANCH, branch)
            try {
                assertTrue(commit.isNotBlank(), "Commit returned")
                assertEquals(commit, scm.getBranchLastCommit(branch))
            } finally {
                scm.deleteBranch(branch)
            }
            assertNull(scm.getBranchLastCommit(branch), "Branch has been deleted")
        }
    }

    @TestOnBitbucketCloud
    fun `Uploading and downloading files, and getting the commits between them`() {
        withScm { scm, _, _ ->
            val branch = names.branch("scm-upload")
            val base = scm.createBranch(BitbucketCloudTestFixture.MAIN_BRANCH, branch)
            try {
                val path = "yontrack-test/upload.txt"
                scm.upload(branch, base, path, "First".toByteArray(), "First upload")
                scm.upload(branch, base, path, "Second".toByteArray(), "Second upload")
                val last = scm.getBranchLastCommit(branch) ?: fail("Branch last commit")

                // Downloading
                assertEquals("Second", scm.download(branch, path)?.decodeToString())
                assertEquals("Second", scm.download(last, path)?.decodeToString())
                assertNull(scm.download(branch, "yontrack-test/missing.txt"))

                // Commits between the base and the last commit, in both orders
                val expected = listOf("Second upload", "First upload")
                val commits = runBlocking { scm.getCommits(base, last) }
                assertEquals(expected, commits.map { it.message })
                val reversed = runBlocking { scm.getCommits(last, base) }
                assertEquals(expected, reversed.map { it.message })

                // One commit
                val commit = scm.getCommit(last) ?: fail("Commit $last")
                assertEquals("Second upload", commit.message)
                assertEquals(
                    "https://bitbucket.org/${env.workspace}/${env.repository}/commits/$last",
                    commit.link
                )
            } finally {
                scm.deleteBranch(branch)
            }
        }
    }

    @TestOnBitbucketCloud
    fun `Commits and branches from the local clone`() {
        withScm { scm, _, project ->
            val commits = mutableListOf<SCMCommit>()
            scm.forAllCommits(project, SCMCommitFilter(null, null, 5)) {
                commits += it
            }
            assertTrue(commits.isNotEmpty(), "At least one commit must be present")
            val first = commits.first()
            assertEquals("https://bitbucket.org/${env.workspace}/${env.repository}/commits/${first.id}", first.link)
            assertTrue(
                scm.getBranchesForCommit(project, first.id).isNotEmpty(),
                "The commit belongs to at least one branch"
            )
        }
    }

    @TestOnBitbucketCloud
    fun `Getting a pull request`() {
        withScm { scm, config, _ ->
            val branch = names.branch("scm-pr")
            val base = scm.createBranch(BitbucketCloudTestFixture.MAIN_BRANCH, branch)
            val api = BitbucketCloudTestRestApi.of(env)
            var prId: Int? = null
            try {
                scm.upload(branch, base, "yontrack-test/pr.txt", "PR".toByteArray(), "Change for a pull request")
                val pr = RestTemplateBuilder()
                    .basicAuthentication(env.bot.email, env.bot.token)
                    .build()
                    .postForObject(
                        "${BitbucketCloudTestRestApi.ROOT}/2.0/repositories/${env.workspace}/${env.repository}/pullrequests",
                        mapOf(
                            "title" to "Test PR $branch",
                            "source" to mapOf("branch" to mapOf("name" to branch)),
                            "destination" to mapOf("branch" to mapOf("name" to BitbucketCloudTestFixture.MAIN_BRANCH)),
                        ),
                        JsonNode::class.java
                    ) ?: fail("Pull request created")
                val id = pr.path("id").asInt()
                prId = id

                val gitConfiguration = BitbucketCloudGitConfiguration(
                    BitbucketCloudProjectConfigurationProperty(
                        configuration = config,
                        workspace = env.workspace,
                        repository = env.repository,
                        indexationInterval = 0,
                        issueServiceConfigurationIdentifier = null,
                    ),
                    null
                )
                assertNotNull(bitbucketCloudConfigurator.getPullRequest(gitConfiguration, id)) {
                    assertEquals(id, it.id)
                    assertEquals("#$id", it.key)
                    assertEquals(branch, it.source)
                    assertEquals(BitbucketCloudTestFixture.MAIN_BRANCH, it.target)
                    assertEquals("OPEN", it.status)
                    assertEquals("Test PR $branch", it.title)
                }
                assertNull(bitbucketCloudConfigurator.getPullRequest(gitConfiguration, 999_999), "PR not found")
            } finally {
                prId?.let { api.declinePullRequest(it) }
                scm.deleteBranch(branch)
            }
        }
    }

    /**
     * Auto-versioning pull request between two test branches — never into the main branch of the fixture —
     * approved by the approver identity, merged by Yontrack, its source branch deleted by the merge.
     */
    @TestOnBitbucketCloud
    fun `Auto-versioning pull request approved by the auto merge identity and merged`() {
        withScm { scm, _, _ ->
            val target = names.branch("av-target")
            val source = names.branch("av-source")
            val api = BitbucketCloudTestRestApi.of(env)
            var prId: Int? = null
            var merged = false
            scm.createBranch(BitbucketCloudTestFixture.MAIN_BRANCH, target)
            try {
                val sourceCommit = scm.createBranch(target, source)
                val version = "9.9.${System.currentTimeMillis()}"
                val content = scm.download(source, BitbucketCloudTestFixture.VERSION_FILE)
                    ?.decodeToString()
                    ?: fail("Version file")
                val newContent = content.lines().joinToString("\n") { line ->
                    if (line.startsWith("${BitbucketCloudTestFixture.VERSION_PROPERTY}=")) {
                        "${BitbucketCloudTestFixture.VERSION_PROPERTY}=$version"
                    } else {
                        line
                    }
                }
                scm.upload(
                    scmBranch = source,
                    commit = sourceCommit,
                    path = BitbucketCloudTestFixture.VERSION_FILE,
                    content = newContent.toByteArray(),
                    message = "Upgrading to $version",
                )

                val pr = scm.createPR(
                    from = source,
                    to = target,
                    title = "Test auto-versioning $source",
                    description = "Upgrading to $version",
                    autoApproval = true,
                    remoteAutoMerge = false,
                    message = "Auto-versioning to $version",
                    reviewers = emptyList(),
                )
                prId = pr.id.toInt()
                assertEquals("PR-${pr.id}", pr.name)
                assertEquals(SCMPullRequestStatus.MERGED, pr.status, "Pull request merged")
                merged = true

                assertEquals(
                    newContent.trim(),
                    scm.download(target, BitbucketCloudTestFixture.VERSION_FILE)?.decodeToString()?.trim(),
                    "New version merged into the target branch"
                )
                assertEquals(
                    SCMPullRequestStatus.MERGED,
                    scm.getPullRequestByName(pr.name)?.status,
                    "Pull request by name"
                )
                // The source branch is deleted by the merge, possibly a bit later
                var sourceLeft = scm.getBranchLastCommit(source)
                var attempts = 0
                while (sourceLeft != null && attempts < 5) {
                    Thread.sleep(2_000)
                    sourceLeft = scm.getBranchLastCommit(source)
                    attempts++
                }
                assertNull(sourceLeft, "Source branch deleted by the merge")
            } finally {
                if (!merged) {
                    prId?.let { api.declinePullRequest(it) }
                }
                scm.deleteBranch(source)
                scm.deleteBranch(target)
            }
        }
    }

    private fun createConfig(config: BitbucketCloudConfiguration): BitbucketCloudConfiguration {
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        return config
    }

    private fun extensionPath(config: BitbucketCloudConfiguration, path: String) =
        scmExtension.getSCMPath(config.name, "${env.workspace}/${env.repository}/$path")
            ?: fail("SCM path")

    @Autowired
    private lateinit var scmExtension: BitbucketCloudSCMExtension

    private fun withScm(code: (scm: SCMChangeLogEnabled, config: BitbucketCloudConfiguration, project: Project) -> Unit) {
        val config = createConfig(bitbucketCloudTestConfigReal())
        project {
            setBitbucketCloudProperty(config, repository = env.repository, workspace = env.workspace)
            val scm = scmDetector.getSCM(this) as? SCMChangeLogEnabled
                ?: fail("Bitbucket Cloud SCM with change log for the project")
            code(scm, config, this)
        }
    }
}
