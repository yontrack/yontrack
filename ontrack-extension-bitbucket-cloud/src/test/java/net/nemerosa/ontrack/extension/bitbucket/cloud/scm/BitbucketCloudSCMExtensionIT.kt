package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.*
import net.nemerosa.ontrack.extension.git.property.GitCommitProperty
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogService
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.files.FileRefService
import net.nemerosa.ontrack.model.files.downloadDocument
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@ContextConfiguration(classes = [BitbucketCloudClientMockConfig::class])
@AsAdminTest
class BitbucketCloudSCMExtensionIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var scmDetector: SCMDetector

    @Autowired
    private lateinit var fileRefService: FileRefService

    @Autowired
    private lateinit var scmChangeLogService: SCMChangeLogService

    @Autowired
    private lateinit var clientFactory: BitbucketCloudClientFactory

    private lateinit var client: BitbucketCloudClient

    @BeforeEach
    fun init() {
        client = mockk()
        every { clientFactory.getBitbucketCloudClient(any()) } returns client
        // Setting the commit property of a build indexes its commit
        every { client.getCommit(any(), any(), any()) } returns null
    }

    private fun createConfig(): BitbucketCloudConfiguration {
        val config = bitbucketCloudTestConfigMock()
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        return config
    }

    private fun commit(hash: String) = BitbucketCloudCommit(
        hash = hash,
        date = "2026-09-01T10:20:30+00:00",
        message = "Commit $hash",
        author = BitbucketCloudCommitAuthor(raw = "Author <author@example.com>", user = null),
        links = null,
    )

    @Test
    fun `Project without the Bitbucket Cloud property has no SCM`() {
        project {
            assertNull(scmDetector.getSCM(this))
        }
    }

    @Test
    fun `Project with the Bitbucket Cloud property has a Bitbucket Cloud SCM`() {
        val config = createConfig()
        project {
            setBitbucketCloudProperty(config, repository = "my-repository", workspace = "my-workspace")
            assertNotNull(scmDetector.getSCM(this), "SCM detected") { scm ->
                assertEquals("git", scm.type)
                assertEquals("bitbucket-cloud", scm.engine)
                assertEquals("my-workspace/my-repository", scm.repository)
                assertEquals("https://bitbucket.org/my-workspace/my-repository", scm.repositoryHtmlURL)
                assertEquals("https://bitbucket.org/my-workspace/my-repository.git", scm.repositoryURI)
            }
        }
    }

    @Test
    fun `Change log between two builds`() {
        val config = createConfig()
        every { client.getCommits("my-workspace", "my-repository", "c1", "c3", any()) } returns
                listOf(commit("c3"), commit("c2"))
        project {
            setBitbucketCloudProperty(config, repository = "my-repository", workspace = "my-workspace")
            branch {
                val from = build { gitCommit("c1") }
                val to = build { gitCommit("c3") }
                val changeLog = runBlocking { scmChangeLogService.getChangeLog(from, to) }
                    ?: fail("Change log expected")
                assertEquals(listOf("c3", "c2"), changeLog.commits.map { it.commit.id })
                assertEquals(
                    "https://bitbucket.org/my-workspace/my-repository/commits/c3",
                    changeLog.commits.first().commit.link
                )
            }
        }
    }

    @Test
    fun `Downloading a file using a SCM reference`() {
        val config = createConfig()
        every { client.getRepository("my-workspace", "my-repository") } returns BitbucketCloudRepository(
            uuid = "{repo}",
            slug = "my-repository",
            name = "my-repository",
            updated_on = "",
            created_on = "",
            project = BitbucketCloudProject(uuid = "{prj}", key = "PRJ", name = "Project"),
            mainbranch = BitbucketCloudBranchName("main"),
        )
        every { client.download("my-workspace", "my-repository", "main", "path/to/file.txt") } returns
                "Some content".toByteArray()
        val document = fileRefService.downloadDocument(
            "scm://bitbucket-cloud/${config.name}/my-workspace/my-repository/path/to/file.txt",
            "text/plain"
        ) ?: fail("Document expected")
        assertEquals("Some content", document.content.decodeToString())
    }

    private fun Build.gitCommit(commit: String) {
        setProperty(this, GitCommitPropertyType::class.java, GitCommitProperty(commit))
    }
}
