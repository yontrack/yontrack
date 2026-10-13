package net.nemerosa.ontrack.git

import net.nemerosa.ontrack.git.support.GitConnectionConfig
import net.nemerosa.ontrack.git.support.GitRepo
import net.nemerosa.ontrack.git.support.GitRepositoryClientImpl
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every read from a local clone goes through `ensureFresh()` (#2085): it clones a repository never
 * cloned, fetches a clone older than the freshness window, and re-clones a clone older than the
 * reset generation of its repository.
 */
class GitRepositoryClientFreshnessTest {

    private lateinit var origin: GitRepo
    private lateinit var clones: File
    private val clock = MutableClock(Instant.parse("2026-10-13T10:00:00Z"))
    private var generation = 0L

    @BeforeEach
    fun before() {
        origin = GitRepo()
        origin.apply {
            git("init", "--initial-branch=main")
            commit(1)
            tag("1.0.0")
        }
        clones = Files.createTempDirectory("ontrack-git-clones").toFile()
    }

    @AfterEach
    fun after() {
        origin.close()
        FileUtils.deleteDirectory(clones)
    }

    /**
     * A client for the clone of [origin], as the factory gives one for each use.
     */
    private fun client(): GitRepositoryClient = GitRepositoryClientImpl(
        repositoryDir = File(clones, "origin"),
        repository = GitRepository("test", "origin", "file://${origin.dir.absolutePath}"),
        config = GitConnectionConfig(),
        freshness = FRESHNESS,
        generations = { generation },
        clock = clock,
    )

    @Test
    fun `a read clones a repository never cloned`() {
        assertEquals(listOf("1.0.0"), client().tags.map { it.name })
    }

    @Test
    fun `a read within the freshness window does not fetch`() {
        assertEquals(listOf("1.0.0"), client().tags.map { it.name })
        origin.commit(2)
        origin.tag("1.1.0")
        clock.plus(FRESHNESS.minusSeconds(1))
        assertEquals(listOf("1.0.0"), client().tags.map { it.name }, "Clone not fetched yet")
    }

    @Test
    fun `a read beyond the freshness window fetches`() {
        assertEquals(listOf("1.0.0"), client().tags.map { it.name })
        origin.commit(2)
        origin.tag("1.1.0")
        clock.plus(FRESHNESS)
        assertEquals(listOf("1.0.0", "1.1.0"), client().tags.map { it.name }.sorted(), "Clone fetched")
    }

    @Test
    fun `the same client fetches again once the freshness window is over`() {
        val client = client()
        assertEquals(listOf("1.0.0"), client.tags.map { it.name })
        origin.commit(2)
        origin.tag("1.1.0")
        assertEquals(listOf("1.0.0"), client.tags.map { it.name }, "Clone not fetched yet")
        clock.plus(FRESHNESS)
        assertEquals(listOf("1.0.0", "1.1.0"), client.tags.map { it.name }.sorted(), "Clone fetched")
    }

    @Test
    fun `the synchronisation always fetches`() {
        assertEquals(listOf("1.0.0"), client().tags.map { it.name })
        origin.commit(2)
        origin.tag("1.1.0")
        client().sync { println(it) }
        assertEquals(listOf("1.0.0", "1.1.0"), client().tags.map { it.name }.sorted(), "Clone fetched")
    }

    @Test
    fun `a clone older than the reset generation is cloned again on its next use`() {
        origin.git("branch", "feature")
        assertEquals(listOf("feature", "main"), client().branches.branches.map { it.name })

        // A fetch keeps the branches deleted on the remote
        origin.git("branch", "-D", "feature")
        clock.plus(FRESHNESS)
        assertEquals(listOf("feature", "main"), client().branches.branches.map { it.name }, "Fetched")

        // Reset
        generation++
        assertEquals(listOf("main"), client().branches.branches.map { it.name }, "Cloned again")
    }

    @Test
    fun `a clone older than the reset generation is cloned again by the synchronisation`() {
        origin.git("branch", "feature")
        assertEquals(listOf("feature", "main"), client().branches.branches.map { it.name })
        origin.git("branch", "-D", "feature")
        generation++
        client().sync { println(it) }
        clock.plus(FRESHNESS.minusSeconds(1))
        assertEquals(listOf("main"), client().branches.branches.map { it.name }, "Cloned again")
    }

    @Test
    fun `a clone made before the freshness window existed is fetched on its next use`() {
        // A clone of an older version, without the state of its last fetch
        origin.git("branch", "feature")
        val dir = File(clones, "origin")
        GitRepo(clones).git("clone", origin.dir.absolutePath, dir.absolutePath)
        origin.commit(2)
        origin.tag("1.1.0")
        assertEquals(listOf("1.0.0", "1.1.0"), client().tags.map { it.name }.sorted(), "Fetched")
        assertTrue(File(dir, ".git/refs/remotes/origin").exists(), "Not cloned again")
    }

    @Test
    fun `a read on a clone whose remote cannot be fetched uses the clone as it is`() {
        assertEquals(listOf("1.0.0"), client().tags.map { it.name })
        FileUtils.deleteDirectory(origin.dir)
        clock.plus(FRESHNESS)
        assertEquals(listOf("1.0.0"), client().tags.map { it.name }, "Clone as it is")
    }

    @Test
    fun `a repository without a remote is never fetched`() {
        // A local repository, read where it is
        val client = GitRepositoryClientImpl(
            repositoryDir = origin.dir,
            repository = GitRepository("test", "local", ""),
            config = GitConnectionConfig(),
            freshness = FRESHNESS,
            generations = { generation },
            clock = clock,
        )
        generation++
        assertEquals(listOf("1.0.0"), client.tags.map { it.name })
        assertFalse(File(origin.dir, ".git/yontrack-clone.properties").exists(), "No state of the clone")
    }

    private class MutableClock(private var instant: Instant) : Clock() {
        fun plus(duration: Duration) {
            instant = instant.plus(duration)
        }

        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    companion object {
        private val FRESHNESS = Duration.ofMinutes(1)
    }
}
