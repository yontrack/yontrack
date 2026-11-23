package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.mock.MockSCMExtension.MockRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockSCMGitMirrorTest {

    @TempDir
    lateinit var remote: File

    private fun config(
        pushUrl: String = remote.toURI().toString(),
        cloneUrl: String = "http://git.test/mock",
        username: String = "",
        password: String = "",
    ) = SCMExtensionConfigProperties.MockSCMGitConfigProperties().apply {
        this.pushUrl = pushUrl
        this.cloneUrl = cloneUrl
        this.username = username
        this.password = password
    }

    private fun bareRemote(name: String): Repository {
        // JGit does not create a missing repository when pushing over file://
        return Git.init().setBare(true).setDirectory(File(remote, "$name.git")).call().repository
    }

    private fun repository(name: String) = MockRepository(name).apply {
        registerFile("main", "gradle.properties", "version=1")
        registerFile("main", "dir/nested.txt", "nested")
        createBranch("main", "feature/upgrade")
        registerFile("feature/upgrade", "gradle.properties", "version=2")
    }

    private fun Repository.commit(branch: String): RevCommit =
        RevWalk(this).use { it.parseCommit(resolve("refs/heads/$branch")) }

    private fun Repository.content(branch: String, path: String): String? =
        RevWalk(this).use { walk ->
            val commit = walk.parseCommit(resolve("refs/heads/$branch"))
            TreeWalk.forPath(this, path, commit.tree)?.let { String(open(it.getObjectId(0)).bytes) }
        }

    @Test
    fun `Branches, files and history are pushed`() {
        val bare = bareRemote("repo")
        MockSCMGitMirror(config()).publish(repository("repo").toData())

        val main = bare.commit("main")
        val feature = bare.commit("feature/upgrade")
        assertEquals(0, main.parentCount)
        assertEquals(1, feature.parentCount)
        assertEquals(main.id, feature.getParent(0).id)
        assertEquals("version=1", bare.content("main", "gradle.properties"))
        assertEquals("nested", bare.content("main", "dir/nested.txt"))
        assertEquals("version=2", bare.content("feature/upgrade", "gradle.properties"))
        assertEquals("nested", bare.content("feature/upgrade", "dir/nested.txt"))
    }

    @Test
    fun `A created branch without change points at its source commit`() {
        val bare = bareRemote("repo")
        val repo = repository("repo")
        repo.createBranch("main", "same")
        MockSCMGitMirror(config()).publish(repo.toData())
        assertEquals(bare.commit("main").id, bare.commit("same").id)
    }

    @Test
    fun `A branch with registered commits only is pushed with an empty tree`() {
        val bare = bareRemote("repo")
        val repo = MockRepository("repo")
        repo.registerCommit("release", "Some commit")
        MockSCMGitMirror(config()).publish(repo.toData())
        assertEquals(0, bare.commit("release").parentCount)
        assertNull(bare.content("release", "gradle.properties"))
    }

    @Test
    fun `A second push force-updates the refs`() {
        val bare = bareRemote("repo")
        val repo = repository("repo")
        val mirror = MockSCMGitMirror(config())
        mirror.publish(repo.toData())
        val before = bare.commit("main").id

        repo.registerFile("main", "gradle.properties", "version=3")
        mirror.publish(repo.toData())

        assertTrue(before != bare.commit("main").id)
        assertEquals("version=3", bare.content("main", "gradle.properties"))
        assertEquals(1, bare.commit("feature/upgrade").parentCount)
    }

    @Test
    fun `The clone access carries the URL and the credentials`() {
        bareRemote("repo")
        val access = MockSCMGitMirror(config(cloneUrl = "http://git.test/mock/", username = "u", password = "p"))
            .publish(repository("repo").toData())
        assertNotNull(access)
        assertEquals("http://git.test/mock/repo.git", access.url)
        assertEquals("u", access.credentials?.username)
        assertEquals("p", access.credentials?.password)
    }

    @Test
    fun `No credentials with a blank username`() {
        bareRemote("repo")
        val access = MockSCMGitMirror(config(password = "ignored")).publish(repository("repo").toData())
        assertNotNull(access)
        assertEquals("http://git.test/mock/repo.git", access.url)
        assertNull(access.credentials)
    }

    @Test
    fun `Nothing is done when not configured`() {
        val bare = bareRemote("repo")
        assertNull(MockSCMGitMirror(config(pushUrl = "")).publish(repository("repo").toData()))
        assertNull(MockSCMGitMirror(config(cloneUrl = " ")).publish(repository("repo").toData()))
        assertNull(MockSCMGitMirror(SCMExtensionConfigProperties().mock.git).publish(repository("repo").toData()))
        assertTrue(bare.refDatabase.getRefsByPrefix(Constants.R_HEADS).isEmpty())
    }

    @Test
    fun `A push failure is reported`() {
        // No bare repository at the destination
        val ex = assertFailsWith<MockSCMGitMirrorException> {
            MockSCMGitMirror(config()).publish(repository("missing").toData())
        }
        assertTrue(ex.message!!.contains("missing.git"), ex.message)
    }
}
