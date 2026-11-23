package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.extension.scm.service.SCMCloneCredentials
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Date
import java.util.Comparator
import java.util.TimeZone

/**
 * Publishes a mock SCM repository to a real Git server, so that a process outside Yontrack can
 * clone it and push to it.
 *
 * **Development and test fixture only** (#2172), off by default: nothing happens unless both
 * `ontrack.config.extension.scm.mock.git.push-url` and `...clone-url` are set.
 *
 * The in-memory repository is materialised into a temporary local Git repository using JGit (the
 * `git` binary may be missing from the container) and every branch is force-pushed to
 * `<push-url>/<repository>.git`. The server is expected to create the repository on the first
 * push.
 *
 * The branches are the ones with files, with registered commits, or created through
 * [MockSCMExtension.MockRepository.createBranch]. The `""` key of the files is the branch of a
 * download without any branch, not a branch: it is not published. A plain branch is one commit
 * of its files; a created branch is its source's commit plus one commit for its own files, if
 * they change anything. A branch without any file gets a commit with an empty tree. The
 * history is deterministic: fixed identity and date.
 *
 * The mirror goes one way, and every call replaces the branches of the server: what was pushed
 * there since - the commit of a job - is neither read back into the mock SCM nor kept by the next
 * call on the same repository, and a branch deleted from the mock SCM stays on the server. Good
 * enough for a test which runs one order per repository.
 */
class MockSCMGitMirror(
    private val config: SCMExtensionConfigProperties.MockSCMGitConfigProperties,
) {

    /**
     * Whether a Git server is configured: both the push and the clone URLs are set.
     */
    val configured: Boolean
        get() = config.pushUrl.isNotBlank() && config.cloneUrl.isNotBlank()

    /**
     * @return The clone access to the published repository, or `null` when not configured, in
     * which case nothing is done.
     * @throws MockSCMGitMirrorException When the push fails
     */
    fun publish(data: MockRepositoryData): SCMCloneAccess? {
        if (!configured) return null
        val remoteName = "${data.name}.git"
        val pushUrl = "${config.pushUrl.trimEnd('/')}/$remoteName"
        val dir = Files.createTempDirectory("yontrack-mock-scm-").toFile()
        try {
            push(data, dir, pushUrl)
        } catch (e: MockSCMGitMirrorException) {
            throw e
        } catch (e: Exception) {
            throw MockSCMGitMirrorException("Cannot push the mock SCM repository ${data.name} to $pushUrl: ${e.message}", e)
        } finally {
            deleteRecursively(dir)
        }
        return SCMCloneAccess(
            url = "${config.cloneUrl.trimEnd('/')}/$remoteName",
            credentials = config.username.takeIf { it.isNotBlank() }?.let {
                SCMCloneCredentials(it, config.password)
            },
        )
    }

    private fun push(data: MockRepositoryData, dir: File, pushUrl: String) {
        Git.init().setDirectory(dir).call().use { git ->
            val repo = git.repository
            materialise(repo, data)
            val command = git.push()
                .setRemote(pushUrl)
                .setRefSpecs(RefSpec("+refs/heads/*:refs/heads/*"))
            if (config.username.isNotBlank()) {
                command.setCredentialsProvider(UsernamePasswordCredentialsProvider(config.username, config.password))
            }
            command.call().forEach { result ->
                result.remoteUpdates.forEach { update ->
                    if (update.status != RemoteRefUpdate.Status.OK && update.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                        throw MockSCMGitMirrorException(
                            "Cannot push the mock SCM repository ${data.name} to $pushUrl: " +
                                    "${update.remoteName} ${update.status} ${update.message ?: ""}".trim()
                        )
                    }
                }
            }
        }
    }

    private fun materialise(repo: Repository, data: MockRepositoryData) {
        val files = data.files.filter { it.scmBranch.isNotEmpty() }
            .groupBy { it.scmBranch }
            .mapValues { (_, list) -> list.associate { it.path to it.content } }
        val created = data.createdBranches.associate { it.branch to it.from }
        val names = (files.keys + data.branches.map { it.name } + created.keys)
            .filter { it.isNotEmpty() }
            .toSortedSet()

        val identity = PersonIdent(AUTHOR_NAME, AUTHOR_EMAIL, Date.from(FIXED_TIME), TimeZone.getTimeZone("UTC"))
        val commits = mutableMapOf<String, ObjectId>()
        val trees = mutableMapOf<String, Map<String, String>>()

        repo.newObjectInserter().use { inserter ->
            fun build(name: String, visiting: Set<String>): ObjectId {
                commits[name]?.let { return it }
                check(name !in visiting) { "Cycle in the created branches of the mock SCM: $name" }
                val own = files[name] ?: emptyMap()
                val source = created[name]
                val parent = source?.takeIf { it in names }?.let { build(it, visiting + name) }
                val tree = (source?.let { trees[it] } ?: emptyMap()) + own
                trees[name] = tree
                val id = if (parent != null && tree == trees[source]) {
                    parent
                } else {
                    val builder = CommitBuilder()
                    builder.setTreeId(writeTree(inserter, tree))
                    parent?.let { builder.setParentId(it) }
                    builder.author = identity
                    builder.committer = identity
                    builder.message = "Mock SCM branch $name"
                    inserter.insert(builder).also { inserter.flush() }
                }
                commits[name] = id
                return id
            }
            names.forEach { build(it, emptySet()) }
        }
        commits.forEach { (name, id) ->
            val ref = repo.updateRef(Constants.R_HEADS + name)
            ref.setNewObjectId(id)
            ref.setForceUpdate(true)
            val result = ref.update()
            if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NO_CHANGE) {
                throw MockSCMGitMirrorException("Cannot create the branch $name of the mock SCM repository ${data.name}: $result")
            }
        }
    }

    private fun writeTree(inserter: ObjectInserter, tree: Map<String, String>): ObjectId {
        val cache = DirCache.newInCore()
        val builder = cache.builder()
        tree.toSortedMap().forEach { (path, content) ->
            val entry = DirCacheEntry(path)
            entry.fileMode = FileMode.REGULAR_FILE
            entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content.toByteArray(Charsets.UTF_8)))
            builder.add(entry)
        }
        builder.finish()
        return cache.writeTree(inserter)
    }

    private fun deleteRecursively(dir: File) {
        if (dir.exists()) {
            Files.walk(dir.toPath()).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
            }
        }
    }

    companion object {
        private const val AUTHOR_NAME = "Yontrack Mock SCM"
        private const val AUTHOR_EMAIL = "no-reply@yontrack.invalid"
        private val FIXED_TIME: Instant = Instant.parse("2020-01-01T00:00:00Z")
    }
}

/**
 * Failure to publish a mock SCM repository to its Git server.
 */
class MockSCMGitMirrorException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
