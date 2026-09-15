package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.*
import net.nemerosa.ontrack.extension.bitbucket.cloud.settings.BitbucketCloudSettings
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class BitbucketCloudSCMExtensionTest {

    private val config = bitbucketCloudTestConfigMock(name = "config")
    private lateinit var client: BitbucketCloudClient
    private lateinit var extension: BitbucketCloudSCMExtension

    @BeforeEach
    fun init() {
        client = mockk()
        val clientFactory = mockk<BitbucketCloudClientFactory>()
        every { clientFactory.getBitbucketCloudClient(config) } returns client
        val configurationService = mockk<BitbucketCloudConfigurationService>()
        every { configurationService.findConfiguration("config") } returns config
        every { configurationService.findConfiguration("missing") } returns null
        val cachedSettingsService = mockk<CachedSettingsService>()
        every { cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java) } returns
                BitbucketCloudSettings(maxCommits = 50)
        extension = BitbucketCloudSCMExtension(
            extensionFeature = mockk(),
            propertyService = mockk(),
            structureService = mockk(),
            clientFactory = clientFactory,
            cachedSettingsService = cachedSettingsService,
            configurationService = configurationService,
            issueServiceRegistry = mockk(),
            gitRepositoryClientFactory = mockk(),
            gitConfigService = mockk(),
            ontrackConfigProperties = mockk(),
        )
    }

    private fun scm(): SCMChangeLogEnabled {
        val (scm, _) = extension.getSCMPath("config", "ws/repo/any") ?: fail("SCM path should have been found")
        return scm as? SCMChangeLogEnabled ?: fail("SCM must support change logs")
    }

    private fun commit(hash: String) = BitbucketCloudCommit(
        hash = hash,
        date = "2026-09-01T10:20:30+02:00",
        message = "Message $hash\n",
        author = BitbucketCloudCommitAuthor(raw = "Git Author <git@example.com>", user = null),
        links = null,
    )

    @Test
    fun `Type is bitbucket-cloud`() {
        assertEquals("bitbucket-cloud", extension.type)
    }

    @Test
    fun `SCM path from a complete reference`() {
        val (scm, path) = extension.getSCMPath("config", "ws/repo/some/path/file.txt")
            ?: fail("SCM path should have been found")
        assertEquals("ws/repo", scm.repository)
        assertEquals("some/path/file.txt", path)
        assertEquals("git", scm.type)
        assertEquals("bitbucket-cloud", scm.engine)
        assertEquals("https://bitbucket.org/ws/repo.git", scm.repositoryURI)
        assertEquals("https://bitbucket.org/ws/repo", scm.repositoryHtmlURL)
    }

    @Test
    fun `SCM path for a missing configuration`() {
        assertNull(extension.getSCMPath("missing", "ws/repo/file.txt"))
    }

    @Test
    fun `SCM path without a path`() {
        assertThrows<InputException> {
            extension.getSCMPath("config", "ws/repo")
        }
    }

    @Test
    fun `Diff link`() {
        assertEquals(
            "https://bitbucket.org/ws/repo/branches/compare/to%0Dfrom#diff",
            scm().getDiffLink("from", "to")
        )
    }

    @Test
    fun `Download on the main branch when no branch is given`() {
        every { client.getRepository("ws", "repo") } returns BitbucketCloudRepository(
            uuid = "{repo}",
            slug = "repo",
            name = "repo",
            updated_on = "",
            created_on = "",
            project = BitbucketCloudProject(uuid = "{prj}", key = "PRJ", name = "Project"),
            mainbranch = BitbucketCloudBranchName("develop"),
        )
        every { client.download("ws", "repo", "develop", "file.txt") } returns "content".toByteArray()
        assertEquals("content", scm().download(null, "file.txt")?.decodeToString())
    }

    @Test
    fun `Commits are capped by the settings`() {
        every { client.getCommits("ws", "repo", "from", "to", 50) } returns listOf(commit("c2"), commit("c1"))
        val commits = runBlocking { scm().getCommits("from", "to") }
        assertEquals(listOf("c2", "c1"), commits.map { it.id })
        val first = commits.first()
        assertEquals("c2", first.shortId)
        assertEquals("Git Author", first.author)
        assertEquals("git@example.com", first.authorEmail)
        assertEquals("Message c2", first.message)
        assertEquals("https://bitbucket.org/ws/repo/commits/c2", first.link)
        assertEquals(8, first.timestamp.hour) // UTC
    }

    @Test
    fun `Commits are looked for in the reverse order when empty`() {
        every { client.getCommits("ws", "repo", "from", "to", 50) } returns emptyList()
        every { client.getCommits("ws", "repo", "to", "from", 50) } returns listOf(commit("c1"))
        val commits = runBlocking { scm().getCommits("from", "to") }
        assertEquals(listOf("c1"), commits.map { it.id })
        verify { client.getCommits("ws", "repo", "to", "from", 50) }
    }

    @Test
    fun `Pull request operations are not supported yet`() {
        val scm = scm()
        assertThrows<BitbucketCloudSCMNotSupportedYetException> {
            scm.createPR("from", "to", "title", "description", false, false, "message", emptyList())
        }
        assertThrows<BitbucketCloudSCMNotSupportedYetException> {
            scm.getPullRequestByName("#1")
        }
        assertThrows<BitbucketCloudSCMNotSupportedYetException> {
            scm.mergeBranch("head", "base")
        }
    }
}
