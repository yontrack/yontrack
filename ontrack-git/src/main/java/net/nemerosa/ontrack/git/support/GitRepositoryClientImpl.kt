package net.nemerosa.ontrack.git.support

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.Utils
import net.nemerosa.ontrack.git.GitCloneGenerations
import net.nemerosa.ontrack.git.GitRepository
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.git.exceptions.*
import net.nemerosa.ontrack.git.model.*
import net.nemerosa.ontrack.git.model.plot.GitPlotRenderer
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Strings
import org.eclipse.jgit.api.*
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revplot.PlotCommitList
import org.eclipse.jgit.revplot.PlotLane
import org.eclipse.jgit.revplot.PlotWalk
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.MessageRevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.String.format
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Stream

/**
 * Client of the local clone of a remote repository.
 *
 * Each replica has clones of its own (#2085). Every read goes through [ensureFresh], which clones the
 * repository on its first use, fetches when the last fetch of the clone is older than the
 * [freshness] window, and clones it again when it was made before the last reset of the repository,
 * its [generation][GitCloneGenerations]. The last fetch and the generation of a clone are kept in the
 * clone itself, so that a client, created for each use, finds them.
 *
 * @param freshness How old the last fetch of the clone can be when it is read
 * @param generations Current reset generation of the repository, the same on every replica
 * @param clock Time of the fetches
 */
class GitRepositoryClientImpl(
    private val repositoryDir: File,
    private val repository: GitRepository,
    private val timeout: Duration = Duration.ofSeconds(60),
    private val operationTimeout: Duration = Duration.ofMinutes(10),
    private val config: GitConnectionConfig,
    private val retries: UInt = 3u,
    private val interval: Duration = Duration.ofSeconds(30),
    private val freshness: Duration = DEFAULT_FRESHNESS,
    private val generations: GitCloneGenerations = GitCloneGenerations.NONE,
    private val clock: Clock = Clock.systemUTC(),
) : GitRepositoryClient {

    private val logger = LoggerFactory.getLogger(GitRepositoryClient::class.java)

    /**
     * Reopened when the clone is made again
     */
    @Volatile
    private var git: Git = openGit()
    private val credentialsProvider: CredentialsProvider? = repository.authenticator?.getCredentialsProvider()

    /**
     * Lock of the clone, shared by all the clients of this replica: a client is created for each use
     */
    private val directoryLock: ReentrantLock = getDirectoryLock(repositoryDir.absolutePath)

    /**
     * When this client last made sure the clone was fresh
     */
    @Volatile
    private var checkedAt: Instant? = null

    private val isClonedOrCloning: Boolean
        get() = File(repositoryDir, ".git").exists()

    override val isReady: Boolean get() = isClonedOrCloning && !directoryLock.isLocked

    /**
     * Runs a remote command, with authentication, timeout & retry management.
     */
    private fun <C : TransportCommand<C, T>, T> C.invoke(message: String): T {
        // Configuration
        setCredentialsProvider(credentialsProvider)
        setTimeout(timeout.toSeconds().toInt())
        // Runs with retries
        return GitConnectionRetry.retry(
            message = message,
            config = config,
            defaultRetries = retries,
            defaultInterval = interval,
        ) {
            runBlocking {
                withTimeout(operationTimeout.toMillis()) {
                    call()
                }
            }
        }
    }

    override val remoteBranches: List<String>
        get() {
            try {
                return git.lsRemote()
                    .setHeads(true)
                    .invoke<LsRemoteCommand, Collection<Ref>>("Getting remote branches at ${repository.remote}")
                    .map { ref -> Strings.CS.removeStart(ref.name, "refs/heads/") }
            } catch (e: GitAPIException) {
                throw GitRepositoryAPIException(repository.remote, e)
            }

        }

    override val branches: GitBranchesInfo
        get() {
            ensureFresh()
            return if (!isClonedOrCloning) {
                GitBranchesInfo.empty()
            } else try {
                val repo = git.repository
                val revWalk = RevWalk(repo)
                val branchRefs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                val index = TreeMap<String, GitCommit>()
                for (ref in branchRefs) {
                    val branchName = Strings.CS.removeStart(ref.name, "refs/remotes/origin/")
                    if (!Strings.CS.equals("HEAD", branchName)) {
                        val revCommit = revWalk.parseCommit(ref.objectId)
                        val gitCommit = toCommit(revCommit)
                        index[branchName] = gitCommit
                    }
                }
                GitBranchesInfo(
                    index.entries
                        .map { entry -> GitBranchInfo(entry.key, entry.value) }
                )
            } catch (e: GitAPIException) {
                throw GitRepositoryAPIException(repository.remote, e)
            } catch (e: IOException) {
                throw GitRepositoryIOException(repository.remote, e)
            }
        }

    override val defaultBranch: String?
        get() {
            ensureFresh()
            return if (!isClonedOrCloning) {
                null
            } else try {
                git.repository.branch
            } catch (e: IOException) {
                throw GitRepositoryIOException(repository.remote, e)
            }
        }

    override fun getBranchesForCommit(commit: String): List<String> {
        ensureFresh()
        try {
            val list = git.branchList()
                .setContains(commit)
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call()
            return list.asSequence().map {
                Strings.CS.removeStart(it.name, "refs/remotes/origin/")
            }.map {
                Strings.CS.removeStart(it, "refs/heads/")
            }.filter {
                it != "HEAD"
            }.distinct().sorted().toList()
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    override val tags: Collection<GitTag>
        get() {
            ensureFresh()
            try {
                val repo = git.repository
                val revWalk = RevWalk(repo)
                return repo.refDatabase.getRefsByPrefix(Constants.R_TAGS)
                    .map { ref -> getGitTagFromRef(revWalk, ref) }
            } catch (e: IOException) {
                throw GitRepositoryIOException(repository.remote, e)
            }
        }

    /**
     * Opens the clone, which may not exist yet. The Git directory is the one of the clone, never one
     * of its parents.
     */
    private fun openGit(): Git =
        try {
            Git(
                FileRepositoryBuilder()
                    .setGitDir(File(repositoryDir, ".git"))
                    .setWorkTree(repositoryDir)
                    .build()
            )
        } catch (e: IOException) {
            throw GitRepositoryInitException(e)
        }

    /**
     * Tries to ls-remote the heads
     */
    override fun test() {
        logger.debug(format("[git] Listing the remote heads in %s", repository.remote))
        try {
            git.lsRemote()
                .setRemote(repository.remote)
                .setHeads(true)
                .invoke("Listing remote heads at ${repository.remote}")
        } catch (e: GitAPIException) {
            throw GitTestException(e.message)
        }

    }

    /**
     * Fetches the clone whatever the time of its last fetch, cloning it if needed, or again when it
     * is older than the reset generation of the repository.
     */
    override fun sync(logger: Consumer<String>) {
        if (directoryLock.tryLock()) {
            try {
                val now = clock.instant()
                val generation = generations.getGeneration(repository)
                val state = readState()
                if (state == null || state.generation < generation) {
                    cloneRemote(generation, logger)
                } else {
                    fetch(logger)
                    writeState(state.generation, now)
                }
                checkedAt = now
            } finally {
                directoryLock.unlock()
            }
        } else {
            logger.accept(format("[git] %s is already synchronising, trying later", repository.remote))
        }
    }

    /**
     * Makes sure the clone can be read: cloned if never done, or done before the last reset of the
     * repository; fetched if its last fetch is older than the [freshness] window. A clone whose
     * remote cannot be fetched is read as it is.
     *
     * A repository without a remote is a local one, read where it is.
     */
    private fun ensureFresh() {
        if (repository.remote.isBlank()) return
        val now = clock.instant()
        val checked = checkedAt
        if (checked != null && Duration.between(checked, now) < freshness) return
        directoryLock.lock()
        try {
            val generation = generations.getGeneration(repository)
            val state = readState()
            if (state == null || state.generation < generation) {
                cloneRemote(generation) { logger.debug(it) }
            } else if (Duration.between(state.fetchedAt, now) >= freshness) {
                try {
                    fetch { logger.debug(it) }
                } catch (any: RuntimeException) {
                    logger.warn("[git] Cannot fetch ${repository.remote}, reading its clone as it is", any)
                }
                // Not tried again before the end of the window, even when it failed
                writeState(state.generation, now)
            }
            checkedAt = now
        } finally {
            directoryLock.unlock()
        }
    }

    private fun catchRemote(
        code: () -> Unit,
    ) {
        try {
            code()
        } catch (noRemote: InvalidRemoteException) {
            throw GitRepositoryNoRemoteException(repository.remote)
        } catch (noRemote: NoRemoteRepositoryException) {
            throw GitRepositoryNoRemoteException(repository.remote)
        }
    }

    private fun fetch(logger: Consumer<String>) {
        logger.accept(format("[git] Pulling %s", repository.remote))
        try {
            catchRemote {
                git.fetch().invoke("Fetching ${repository.remote}")
            }
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        }

        logger.accept(format("[git] Pulling done for %s", repository.remote))
    }

    /**
     * Clones the repository, deleting any clone first, and records the reset [generation] it was
     * cloned at.
     */
    private fun cloneRemote(generation: Long, logger: Consumer<String>) {
        val now = clock.instant()
        git.close()
        deleteClone()
        logger.accept(format("[git] Cloning %s", repository.remote))
        try {
            catchRemote {
                CloneCommand()
                    .setDirectory(repositoryDir)
                    .setURI(repository.remote)
                    .invoke("Cloning ${repository.remote}")
                    .close()
            }
        } catch (e: GitAPIException) {
            deleteClone()
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (any: RuntimeException) {
            // No half clone left, taken for a clone by the next use
            deleteClone()
            throw any
        } finally {
            git = openGit()
        }

        // Check
        if (!isClonedOrCloning) {
            throw GitRepositoryCannotCloneException(repository.remote)
        }
        writeState(generation, now)
        // Done
        logger.accept(format("[git] Clone done for %s", repository.remote))
    }

    /**
     * Deletes the content of the clone, keeping its directory.
     */
    private fun deleteClone() {
        try {
            if (repositoryDir.exists()) {
                FileUtils.cleanDirectory(repositoryDir)
            }
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    /**
     * State of the clone: the reset generation it was made at, and its last fetch.
     */
    private data class CloneState(val generation: Long, val fetchedAt: Instant)

    private val stateFile: File get() = File(repositoryDir, ".git/$STATE_FILE")

    /**
     * Reads the state of the clone, `null` when there is no clone. A clone made by a version
     * without this state is at generation 0, and was never fetched.
     */
    private fun readState(): CloneState? =
        if (!isClonedOrCloning) {
            null
        } else if (!stateFile.exists()) {
            CloneState(0, Instant.EPOCH)
        } else {
            val properties = Properties()
            stateFile.inputStream().use { properties.load(it) }
            CloneState(
                generation = properties.getProperty("generation")?.toLongOrNull() ?: 0,
                fetchedAt = properties.getProperty("fetchedAt")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    ?: Instant.EPOCH,
            )
        }

    private fun writeState(generation: Long, fetchedAt: Instant) {
        val properties = Properties()
        properties.setProperty("generation", generation.toString())
        properties.setProperty("fetchedAt", fetchedAt.toEpochMilli().toString())
        stateFile.outputStream().use { properties.store(it, "State of the clone (#2085)") }
    }


    override fun isCompatible(repository: GitRepository): Boolean {
        return this.repository == repository
    }

    override fun <T> forEachCommitFrom(
        branch: String,
        commit: String,
        include: Boolean,
        code: (RevCommit) -> T?,
    ): T? {
        ensureFresh()
        try {
            val gitRepository = git.repository

            val oCommit = gitRepository.resolve(commit)

            if (include) {
                val revCommit = gitRepository.parseCommit(oCommit)
                val value = code(revCommit)
                if (value != null) {
                    return value
                }
            }

            val oHead: ObjectId = gitRepository.resolve(getBranchRef(branch))
                ?: gitRepository.resolve("refs/heads/$branch")
                ?: throw IllegalStateException("Cannot resolve commit for branch $branch")

            val walk = RevWalk(gitRepository)

            walk.markUninteresting(walk.lookupCommit(oCommit))
            walk.markStart(walk.lookupCommit(oHead))
            walk.sort(RevSort.COMMIT_TIME_DESC, true)
            walk.sort(RevSort.REVERSE, true)

            val i = walk.iterator()
            while (i.hasNext()) {
                val revCommit = i.next()
                val value = code(revCommit)
                if (value != null) {
                    return value
                }
            }
            // Nothing found
            return null
        } catch (_: NoHeadException) {
            // Ignoring this error, nothing to scan
            return null
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    override fun forEachCommit(code: (GitCommit) -> Unit) {
        ensureFresh()
        try {
            git.log().all().call()
                .map { toCommit(it) }
                .forEach(code)
        } catch (_: NoHeadException) {
            // Ignoring this error, nothing to scan
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    override fun forCommits(
        sinceCommit: String?,
        sinceCommitTimestamp: LocalDateTime?,
        count: Int,
        code: (GitCommit) -> Unit
    ) {
        ensureFresh()
        try {
            val gitRepository = git.repository

            // Get all commits, ordered from newest to oldest by default
            val allRevCommits = git.log().all().call().toList()

            // Reverse to get oldest first and convert to GitCommit
            val oldestFirst = allRevCommits.reversed().map { toCommit(it) }

            // Determine where to start processing
            val startIndex = when {
                sinceCommit != null -> {
                    val commitId = gitRepository.resolve(sinceCommit)
                    if (commitId != null) {
                        // Find the index of this commit by ID
                        val commitIdStr = commitId.name
                        val index = oldestFirst.indexOfFirst { it.id == commitIdStr }
                        if (index < 0) 0 else index + 1
                    } else 0
                }
                sinceCommitTimestamp != null -> {
                    // Find the first commit strictly after this timestamp
                    val index = oldestFirst.indexOfFirst { it.commitTime > sinceCommitTimestamp }
                    if (index < 0) oldestFirst.size else index
                }
                else -> 0
            }

            // Process commits starting from the determined index
            if (startIndex >= 0 && startIndex < oldestFirst.size) {
                var processed = 0
                for (i in startIndex until oldestFirst.size) {
                    val gitCommit = oldestFirst[i]
                    code(gitCommit)
                    processed++

                    // Stop if we've reached the count limit
                    if (processed >= count) {
                        break
                    }
                }
            }
        } catch (_: NoHeadException) {
            // Ignoring this error, nothing to scan
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    override fun log(from: String, to: String): Stream<GitCommit> {
        ensureFresh()
        try {
            val gitRepository = git.repository
            val oFrom = gitRepository.resolve(from)
            val oTo = gitRepository.resolve(to)
            return if (oFrom == null || oTo == null) {
                emptyList<GitCommit>().stream()
            } else {
                git.log()
                    .addRange(oFrom, oTo)
                    .call()
                    .map { this.toCommit(it) }
                    .stream()
            }
        } catch (_: NoHeadException) {
            // Ignoring this error, nothing to scan
            return Stream.empty()
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    override fun graph(from: String, to: String): GitLog {
        ensureFresh()
        try {
            val range = range(from, to, false)
            val walk = PlotWalk(git.repository)

            // Log
            walk.markStart(walk.lookupCommit(range.from.id))
            walk.markUninteresting(walk.lookupCommit(range.to.id))
            val commitList = PlotCommitList<PlotLane>()
            commitList.source(walk)
            commitList.fillTo(Integer.MAX_VALUE)

            // Rendering
            val renderer = GitPlotRenderer(commitList)
            val plot = renderer.plot

            // Gets the commits
            val commits = renderer.commits.map { this.toCommit(it) }

            // OK
            return GitLog(
                plot,
                commits
            )

        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun isPatternFound(token: String): Boolean {
        // Ignore exceptions, in order to ignore Git configuration errors
        // In the Git repo is wrongly configured, the token is considered at not found
        return try {
            ensureFresh()
            val log = git.log()
                .all()
                .setRevFilter(MessageRevFilter.create(token))
                .setMaxCount(1)
            val commits = log.call()
            commits.iterator().hasNext()
        } catch (_: GitRepositoryException) {
            false
        } catch (_: GitAPIException) {
            false
        } catch (_: IOException) {
            false
        }

    }

    override fun findCommitForRegex(branch: String, regex: String): RevCommit? {
        ensureFresh()
        try {
            val resolvedBranch = git.repository.resolve(getBranchRef(branch))
            return if (resolvedBranch != null) {
                val log = git.log()
                    .setRevFilter(MessageRevFilter.create("($regex)"))
                    .setMaxCount(1)
                val commits = log.call()
                val i = commits.iterator()
                if (i.hasNext()) {
                    i.next()
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun getLastCommitForExpression(regex: String): String? {
        ensureFresh()
        return try {
            val log = git.log()
                .all()
                .setRevFilter(MessageRevFilter.create(regex))
                .setMaxCount(1)
            val commits = log.call()
            val i = commits.iterator()
            if (i.hasNext()) {
                getId(i.next())
            } else {
                null
            }
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * **Note**: the JGit library does not support the `git-describe` command yet, hence
     * the use of the command line.
     *
     * @see net.nemerosa.ontrack.git.support.GitClientSupport.tagContains
     */
    override fun getTagsWhichContainCommit(gitCommitId: String): Collection<String> {
        ensureFresh()
        return GitClientSupport.tagContains(repositoryDir, gitCommitId)
    }

    override fun unifiedDiff(from: String, to: String, pathFilter: Predicate<String>): String {
        ensureFresh()
        try {
            val range = range(from, to)

            // Diff command
            var entries = git.diff()
                .setShowNameAndStatusOnly(true)
                .setOldTree(getTreeIterator(range.from.id))
                .setNewTree(getTreeIterator(range.to.id))
                .call()

            // Filtering the entries
            entries = entries.filter { entry -> pathFilter.test(entry.oldPath) || pathFilter.test(entry.newPath) }

            // Output
            val output = ByteArrayOutputStream()

            // Formatting
            val formatter = DiffFormatter(output)
            formatter.setRepository(git.repository)
            entries.forEach { entry -> formatDiffEntry(formatter, entry) }

            // OK
            return Utils.toString(output.toByteArray())

        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun download(branch: String, path: String): String? {
        ensureFresh()
        // Git show
        return GitClientSupport.showPath(repositoryDir, getBranchRef(branch), path)
    }

    private fun formatDiffEntry(formatter: DiffFormatter, entry: DiffEntry) {
        try {
            formatter.format(entry)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun diff(from: String, to: String): GitDiff {
        ensureFresh()
        try {
            val range = range(from, to)

            // Diff command
            val entries = git.diff()
                .setShowNameAndStatusOnly(true)
                .setOldTree(getTreeIterator(range.from.id))
                .setNewTree(getTreeIterator(range.to.id))
                .call()

            // OK
            return GitDiff(
                range.from,
                range.to,
                entries.map { diff ->
                    GitDiffEntry(
                        toChangeType(diff.changeType),
                        diff.oldPath,
                        diff.newPath
                    )
                }
            )

        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun getCommitFor(id: String): GitCommit? {
        ensureFresh()
        return try {
            val repo = git.repository
            val objectId = repo.resolve("$id^0")
            if (objectId != null) {
                toCommit(RevWalk(repo).parseCommit(objectId))
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }

    }

    private fun getGitTagFromRef(revWalk: RevWalk, ref: Ref): GitTag {
        val tagName = StringUtils.substringAfter(
            ref.name,
            Constants.R_TAGS
        )
        try {
            val revCommit = revWalk.parseCommit(ref.objectId)
            val commitTime = revCommit.commitTime
            val tagTime = Time.from(commitTime * 1000L)
            return GitTag(
                tagName,
                tagTime
            )
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun isCommit(commitish: String): Boolean {
        ensureFresh()
        try {
            val repo = git.repository
            return repo.resolve(commitish) != null
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        }

    }

    override fun getBranchRef(branch: String): String {
        return String.format("origin/%s", branch)
    }

    override fun getId(revCommit: RevCommit): String {
        return revCommit.id.name
    }

    override fun getShortId(revCommit: RevCommit): String {
        return try {
            git.repository.newObjectReader().abbreviate(revCommit.id).name()
        } catch (e: IOException) {
            revCommit.id.name
        }

    }

    override fun toCommit(revCommit: RevCommit): GitCommit {
        return GitCommit(
            getId(revCommit),
            getShortId(revCommit),
            toPerson(revCommit.authorIdent),
            toPerson(revCommit.committerIdent),
            Time.from(1000L * revCommit.commitTime),
            revCommit.fullMessage,
            revCommit.shortMessage
        )
    }

    private fun toPerson(ident: PersonIdent): GitPerson {
        return GitPerson(
            ident.name,
            ident.emailAddress
        )
    }

    private fun toChangeType(changeType: DiffEntry.ChangeType): GitChangeType {
        return when (changeType) {
            DiffEntry.ChangeType.ADD -> GitChangeType.ADD
            DiffEntry.ChangeType.COPY -> GitChangeType.COPY
            DiffEntry.ChangeType.DELETE -> GitChangeType.DELETE
            DiffEntry.ChangeType.MODIFY -> GitChangeType.MODIFY
            DiffEntry.ChangeType.RENAME -> GitChangeType.RENAME
            else -> throw IllegalArgumentException("Unknown diff change type: $changeType")
        }
    }

    private fun getTreeIterator(id: ObjectId): AbstractTreeIterator {
        val p = CanonicalTreeParser()
        val db = git.repository
        db.newObjectReader().use { or ->
            p.reset(or, RevWalk(db).parseTree(id))
            return p
        }
    }

    override fun mergeBranch(head: String, base: String): GitCommit {
        directoryLock.lock()
        try {
            // Cloned if never done, and fetched again by the merge itself
            ensureFresh()
            // JGit does not throw on a rejected push: a remote base branch which moved since the
            // fetch is merged again, a bounded number of times
            var rejection = ""
            repeat(MERGE_ATTEMPTS) { attempt ->
                val (newHead, pushRejection) = mergeAndPush(head, base)
                if (pushRejection == null) {
                    // Returns the merge commit
                    val revCommit = RevWalk(git.repository).parseCommit(newHead)
                    return toCommit(revCommit)
                }
                rejection = pushRejection
                logger.warn(
                    "[git] Push of the merge of {} into {} rejected by {} (attempt {}/{}): {}",
                    head, base, repository.remote, attempt + 1, MERGE_ATTEMPTS, pushRejection,
                )
            }
            throw GitRepositoryPushRejectedException(repository.remote, head, base, MERGE_ATTEMPTS, rejection)
        } catch (e: GitAPIException) {
            throw GitRepositoryAPIException(repository.remote, e)
        } catch (e: IOException) {
            throw GitRepositoryIOException(repository.remote, e)
        } finally {
            directoryLock.unlock()
        }
    }

    /**
     * Merges the remote [head] into the latest state of the remote [base] and pushes it.
     *
     * @return The new head of [base] and, when the push was rejected, the reason why
     */
    private fun mergeAndPush(head: String, base: String): Pair<ObjectId, String?> {
        // Fetch latest remote state
        fetch { logger.debug("[git] $it") }
        // Checkout base branch locally, resetting from remote tracking ref
        val localBaseExists = git.repository.findRef("refs/heads/$base") != null
        if (localBaseExists) {
            git.checkout().setName(base).call()
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("refs/remotes/origin/$base").call()
        } else {
            git.checkout().setName(base).setStartPoint("refs/remotes/origin/$base").setCreateBranch(true).call()
        }
        // Merge head into base
        val headRef = git.repository.resolve("refs/remotes/origin/$head")
            ?: error("Cannot resolve remote branch $head in ${repository.remote}")
        val mergeResult = git.merge()
            .include(headRef)
            .setMessage("Merge $head into $base")
            .call()
        if (!mergeResult.mergeStatus.isSuccessful) {
            throw GitRepositoryMergeException(repository.remote, head, base, mergeResult.mergeStatus.name)
        }
        // Push base to remote
        val rejection = git.push()
            .setRemote("origin")
            .add("refs/heads/$base")
            .invoke("Pushing $base to ${repository.remote}")
            .flatMap { it.remoteUpdates }
            .filter {
                it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString { update ->
                listOfNotNull(update.remoteName, update.status.name, update.message).joinToString(" ")
            }
        return mergeResult.newHead to rejection
    }

    companion object {

        /**
         * How old the last fetch of a clone can be when it is read, by default
         */
        val DEFAULT_FRESHNESS: Duration = Duration.ofMinutes(1)

        /**
         * State of a clone, in its Git directory: its reset generation and its last fetch
         */
        private const val STATE_FILE = "yontrack-clone.properties"

        /**
         * Locks of the clones, by directory, for this replica
         */
        private val directoryLocks = ConcurrentHashMap<String, ReentrantLock>()

        /**
         * Number of times a merge is done and pushed before its rejection by the remote fails it.
         */
        private const val MERGE_ATTEMPTS = 3

        private fun getDirectoryLock(path: String): ReentrantLock =
            directoryLocks.computeIfAbsent(path) { ReentrantLock() }
    }

    private fun range(from: String, to: String, reorder: Boolean = true): GitRange {
        val gitRepository = git.repository

        val oFrom = gitRepository.resolve(from)
        val oTo = gitRepository.resolve(to)

        val walk = RevWalk(gitRepository)

        var commitFrom = walk.parseCommit(oFrom)
        var commitTo = walk.parseCommit(oTo)

        if (reorder && commitFrom.commitTime > commitTo.commitTime) {
            val t = commitFrom
            commitFrom = commitTo
            commitTo = t
        }

        return GitRange(commitFrom, commitTo)
    }
}
