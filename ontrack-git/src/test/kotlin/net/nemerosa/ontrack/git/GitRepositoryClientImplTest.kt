package net.nemerosa.ontrack.git

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.git.support.GitRepo
import org.junit.Test
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GitRepositoryClientImplTest {

    /**
     * ```
     *     *   C5 (main)
     *     | * C4 (2.1)
     *     |/
     *     * C3
     *     * C2
     *     * C1
     * ```
     */
    @Test
    fun `Iterating over all commits for two branches`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            commit(3)
            git("checkout", "-b", "2.1")
            commit(4)
            git("checkout", "main")
            commit(5)

            log()
        } and { client, _ ->
            val commits = mutableListOf<String>()
            client.forEachCommit { commit ->
                commits.add(commit.fullMessage.trim())
            }
            assertEquals(
                    (1..5).map { "Commit $it" }.toSet(),
                    commits.toSet()
            )
        }
    }

    /**
     * ```
     *     * C8
     *     * C7
     *     * M6
     *     |\
     *     | * C5 (v2.1, 2.1)
     *     * | C4
     *     | * C3
     *     |/
     *     * C2
     *     * C1
     * ```
     */
    @Test
    fun `Iterating over all commits for two merged branches`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            git("checkout", "-b", "2.1")
            commit(3)
            git("checkout", "main")
            commit(4)
            git("checkout", "2.1")
            commit(5)
            tag("v2.1")
            git("checkout", "main")
            git("merge", "--no-ff", "2.1", "--message", "Merge 2.1") // M6
            commit(7)
            commit(8)

            log()
        } and { client, _ ->
            val commits = mutableListOf<String>()
            client.forEachCommit { commit ->
                commits.add(commit.fullMessage.trim())
            }
            assertEquals(
                    ((8 downTo 7).map { "Commit $it" } + "Merge 2.1" + (5 downTo 1).map { "Commit $it" }).toSet(),
                    commits.toSet()
            )
        }
    }

    /**
     * ```
     *     *   C5 (main)
     *     | * C4 (2.1)
     *     |/
     *     * C3
     *     * C2
     *     * C1
     * ```
     */
    @Test
    fun `Iterating over commits on a branch`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            commit(3)
            git("checkout", "-b", "2.1")
            commit(4)
            git("checkout", "main")
            commit(5)

            log()
        } and { client, repo ->
            val c1 = repo.commitLookup("Commit 1")
            val messages = mutableListOf<String>()
            client.forEachCommitFrom("main", c1) { r ->
                messages.add(r.fullMessage.trim())
                // Going on
                null
            }
            assertEquals(
                    listOf(
                            "Commit 1",
                            "Commit 2",
                            "Commit 3",
                            "Commit 5"
                    ),
                    messages
            )
        }
    }

    /**
     * ```
     *     *   C5 (main)
     *     | * C4 (2.1)
     *     |/
     *     * C3
     *     * C2
     *     * C1
     * ```
     */
    @Test
    fun `Iterating over commits on a branch not including the start`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            commit(3)
            git("checkout", "-b", "2.1")
            commit(4)
            git("checkout", "main")
            commit(5)

            log()
        } and { client, repo ->
            val c1 = repo.commitLookup("Commit 1")
            val messages = mutableListOf<String>()
            client.forEachCommitFrom("main", c1, include = false) { r ->
                messages.add(r.fullMessage.trim())
                // Going on
                null
            }
            assertEquals(
                    listOf(
                            "Commit 2",
                            "Commit 3",
                            "Commit 5"
                    ),
                    messages
            )
        }
    }

    /**
     * ```
     *     *   C4 (main)
     *     | * C3 (2.1)
     *     |/
     *     * C2 (2.0)
     *     * C1
     * ```
     */
    @Test
    fun `List of branches for a commit`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            git("checkout", "-b", "2.0")
            commit(2)
            git("checkout", "-b", "2.1")
            commit(3)
            git("checkout", "main")
            commit(4)

            log()
        } and { _, repo ->
            GitRepo.prepare {
                git("clone", repo.dir.absolutePath, ".")
            } and { cloneClient, cloneRepo ->
                cloneClient.getBranchesForCommit(cloneRepo.commitLookup("Commit 3")).apply {
                    assertEquals(listOf("2.1"), this)
                }
                cloneClient.getBranchesForCommit(cloneRepo.commitLookup("Commit 4")).apply {
                    assertEquals(listOf("main"), this)
                }
                cloneClient.getBranchesForCommit(cloneRepo.commitLookup("Commit 2")).apply {
                    assertEquals(listOf("2.0", "2.1"), this)
                }
                cloneClient.getBranchesForCommit(cloneRepo.commitLookup("Commit 1")).apply {
                    assertEquals(listOf("2.0", "2.1", "main"), this)
                }
            }
        }
    }

    /**
     * <pre>
     *     *   C4 (main)
     *     | * C3 (2.1)
     *     |/
     *     * C2
     *     * C1
     * </pre>
     */
    @Test
    fun `List of local branches with their commits`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            git("checkout", "-b", "2.1")
            commit(3)
            git("checkout", "main")
            commit(4)

            log()
        } and { _, repo ->
            GitRepo.prepare {
                git("clone", repo.dir.absolutePath, ".")
            } and { cloneClient, _ ->
                val branches = cloneClient.branches
                assertEquals(2, branches.branches.size)

                assertEquals("2.1", branches.branches[0].name)
                assertEquals("Commit 3", branches.branches[0].commit.shortMessage)

                assertEquals("main", branches.branches[1].name)
                assertEquals("Commit 4", branches.branches[1].commit.shortMessage)
            }
        }
    }

    @Test
    fun `Log between HEAD and a commit ~ 1`() {
        GitRepo.prepare {
            gitInit()
            (1..6).forEach {
                commit(it)
            }

            log()
        } and { repoClient, repo ->
            val commit4 = repo.commitLookup("Commit 4")
            val log = repoClient.log("$commit4~1", "HEAD").collect(Collectors.toList())
            assertEquals(
                    listOf("Commit 6", "Commit 5", "Commit 4"),
                    log.map { it.shortMessage }
            )
        }
    }

    @Test
    fun `Graph between commits`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            commit(3)
            tag("v3")
            commit(4)
            tag("v4")
            commit(5)
            commit(6)
            commit(7)
            tag("v7")
            commit(8)

            log()
        } and { repoClient, repo ->
            val commit4 = repo.commitLookup("Commit 4")
            val commit7 = repo.commitLookup("Commit 7")
            val log = repoClient.graph(commit7, commit4)
            assertEquals(
                    listOf("Commit 7", "Commit 6", "Commit 5"),
                    log.commits.map { it.shortMessage }
            )
        }
    }

    /**
     * What is the change log for 2.2 since 2.1?
     * <pre>
     *     | * C11 (v2.2, 2.2)
     *     * | C10
     *     | * C9
     *     | * C8
     *     |/
     *     * C7
     *     * M6
     *     |\
     *     | * C5 (v2.1, 2.1)
     *     * | C4
     *     | * C3
     *     |/
     *     * C2
     *     * C1
     * </pre>
     *
     * We expect C4, M6, C7, C8, C10, C11
     */
    @Test
    fun `Log between tags on different branches`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            git("checkout", "-b", "2.1")
            commit(3)
            git("checkout", "main")
            commit(4)
            git("checkout", "2.1")
            commit(5)
            tag("v2.1")
            git("checkout", "main")
            git("merge", "--no-ff", "2.1", "--message", "Merge 2.1") // M6
            commit(7)
            git("checkout", "-b", "2.2")
            commit(8)
            commit(9)
            git("checkout", "main")
            commit(10)
            git("checkout", "2.2")
            commit(11)
            tag("v2.2")

            log()
        } and { repoClient, _ ->
            val log = repoClient.graph("v2.2", "v2.1")
            assertEquals(
                    listOf("Commit 11", "Commit 9", "Commit 8", "Commit 7", "Merge 2.1", "Commit 4"),
                    log.commits.map {
                        it.shortMessage
                    }
            )
        }
    }

    /**
     * What is the change log for 2.2 since 2.1?
     *
     * We expect C4, C7, C8, C10, C11, Merge 2.1->2.2, C14
     *
     * @see #prepareBranches(GitRepo)
     */
    @Test
    fun `Log between tags on different hierarchical branches`() {
        GitRepo.prepare {
            prepareBranches()
        } and { repoClient, _ ->
            val log = repoClient.graph("v2.2", "v2.1")
            assertEquals(
                    listOf(
                            "Commit 14",
                            "Merge 2.1->2.2",
                            "Commit 11",
                            "Commit 10",
                            "Commit 8",
                            "Commit 7",
                            "Commit 4"),
                    log.commits.map { it.shortMessage }
            )
        }
    }

    /**
     * Getting the tag for a commit
     */
    @Test
    fun `Tag containing a commit`() {
        GitRepo.prepare {
            prepareBranches()
        } withClone { client, clientRepo, _ ->
            client.sync(Consumer { println(it) })
            // No further tag
            assertEquals(
                    emptyList(),
                    client.getTagsWhichContainCommit(clientRepo.commitLookup("Commit 13"))
            )
            // Exact tag
            assertEquals(
                    listOf("v2.2"),
                    client.getTagsWhichContainCommit(clientRepo.commitLookup("Commit 14"))
            )
            // Several tags, including current commit
            assertEquals(
                    listOf("v2.1", "v2.2"),
                    client.getTagsWhichContainCommit(clientRepo.commitLookup("Commit 12"))
            )
            // Several tags
            assertEquals(
                    listOf("v2.1", "v2.2"),
                    client.getTagsWhichContainCommit(clientRepo.commitLookup("Commit 9"))
            )
        }
    }

    @Test
    fun `Log between tags`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            commit(3)
            tag("v3")
            commit(4)
            tag("v4")
            commit(5)
            commit(6)
            commit(7)
            tag("v7")
            commit(8)
            log()
        } and { client, _ ->
            val log = client.graph("v7", "v4")
            assertEquals(
                    listOf("Commit 7", "Commit 6", "Commit 5"),
                    log.commits.map { it.shortMessage }
            )
        }
    }

    @Test
    fun `Collection of remote branches`() {
        GitRepo.prepare {
            // Initialises a Git repository
            gitInit()

            // Commits 1..4, each one a branch
            (1..4).forEach {
                commit(it)
                git("branch", "feature/$it", "HEAD")
            }

            // Log
            log()
        } and { _, repo ->
            GitRepo.prepare {
                git("clone", repo.dir.absolutePath, ".")
            } and { cloneClient, _ ->
                val branches = cloneClient.remoteBranches
                // Checks the list
                assertEquals(
                        (1..4).map { "feature/$it" } + listOf("main"),
                        branches.sorted()
                )
            }
        }
    }

    @Test
    fun `Get tags`() {
        GitRepo.prepare {
            prepareBranches()
        } withClone { client, _, _ ->
            client.sync(Consumer { println(it) })
            val expectedDate = Time.now().toLocalDate()
            assertEquals(
                    listOf("v2.1", "v2.2"),
                    client.tags.map { it.name }
            )
            assertEquals(
                    listOf(expectedDate, expectedDate),
                    client.tags.map { it.time.toLocalDate() }
            )
        }
    }

    @Test
    fun `Clone and fetch`() {
        GitRepo.prepare {
            // Initialises a Git repository
            gitInit()
            // Commits 1..4
            (1..4).forEach {
                commit(it)
            }
        } withClone { clone, cloneRepo, origin ->

            // First sync (clone)
            clone.sync(Consumer { println(it) })

            // Gets the commits
            (1..4).forEach {
                assertNotNull(cloneRepo.commitLookup("Commit $it"))
            }

            // Adds some commits on the origin repo
            origin.apply {
                (5..8).forEach {
                    commit(it)
                }
            }

            // Second sync (fetch)
            clone.sync(Consumer { println(it) })

            // Gets the commits
            (5..8).forEach {
                assertNotNull(cloneRepo.commitLookup("Commit $it"))
            }
        }
    }
    @Test
    fun `forCommits with count limit`() {
        GitRepo.prepare {
            gitInit()
            (1..10).forEach {
                commit(it)
            }
            log()
        } and { client, _ ->
            val commits = mutableListOf<String>()
            client.forCommits(
                sinceCommit = null,
                sinceCommitTimestamp = null,
                count = 5
            ) { commit ->
                commits.add(commit.shortMessage)
            }
            assertEquals(
                listOf("Commit 1", "Commit 2", "Commit 3", "Commit 4", "Commit 5"),
                commits
            )
        }
    }

    @Test
    fun `forCommits from specific commit`() {
        GitRepo.prepare {
            gitInit()
            (1..10).forEach {
                commit(it)
            }
            log()
        } and { client, repo ->
            val c5 = repo.commitLookup("Commit 5")
            val commits = mutableListOf<String>()
            client.forCommits(
                sinceCommit = c5,
                sinceCommitTimestamp = null,
                count = 100
            ) { commit ->
                commits.add(commit.shortMessage)
            }
            assertEquals(
                (6..10).map { "Commit $it" },
                commits
            )
        }
    }

    @Test
    fun `forCommits with timestamp filter`() {
        GitRepo.prepare {
            gitInit()
            commit(1, pause = true)
            commit(2, pause = true)
            commit(3, pause = true)
            log()
        } and { client, repo ->
            // Get timestamp of commit 2
            val c2Hash = repo.commitLookup("Commit 2")
            val c2Commit = client.getCommitFor(c2Hash)
            assertNotNull(c2Commit)

            val commits = mutableListOf<String>()
            client.forCommits(
                sinceCommit = null,
                sinceCommitTimestamp = c2Commit.commitTime,
                count = 100
            ) { commit ->
                commits.add(commit.shortMessage)
            }
            assertEquals(
                listOf("Commit 3"),
                commits
            )
        }
    }

    /**
     * ```
     *     *   C5 (main)
     *     | * C4 (2.1)
     *     |/
     *     * C3
     *     * C2
     *     * C1
     * ```
     */
    @Test
    fun `forCommits iterates over all branches`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            commit(2)
            commit(3)
            git("checkout", "-b", "2.1")
            commit(4)
            git("checkout", "main")
            commit(5)
            log()
        } and { client, _ ->
            val commits = mutableListOf<String>()
            client.forCommits(
                sinceCommit = null,
                sinceCommitTimestamp = null,
                count = 100
            ) { commit ->
                commits.add(commit.shortMessage)
            }
            assertEquals(
                (1..5).map { "Commit $it" }.toSet(),
                commits.toSet()
            )
        }
    }

    @Test
    fun `forCommits from beginning of time`() {
        GitRepo.prepare {
            gitInit()
            (1..3).forEach {
                commit(it)
            }
            log()
        } and { client, _ ->
            val commits = mutableListOf<String>()
            client.forCommits(
                sinceCommit = null,
                sinceCommitTimestamp = null,
                count = 100
            ) { commit ->
                commits.add(commit.shortMessage)
            }
            assertEquals(
                listOf("Commit 1", "Commit 2", "Commit 3"),
                commits
            )
        }
    }

    /**
     * Prepares some branches in a test repo.
     * <pre>
     *     | * C14 (v2.2, 2.2)
     *     * | C13
     *     | * Merge 2.1->2.2
     *     | |\_________________
     *     | |                  \
     *     | |                  * C12 (v2.1, 2.1)
     *     | * C11              |
     *     | * C10              |
     *     |/                   |
     *     |                    * C9
     *     * C8                 |
     *     * C7                 |
     *     |                    |
     *     |                    * C6
     *     |                    * C5
     *     *                    | C4
     *     |                    * C3
     *     |--------------------/
     *     * C2
     *     * C1
     * </pre>
     */
    private fun GitRepo.prepareBranches() {
        gitInit()
        commit(1)
        commit(2)
        git("checkout", "-b", "2.1")
        commit(3)
        git("checkout", "main")
        commit(4)
        git("checkout", "2.1")
        commit(5)
        commit(6)
        git("checkout", "main")
        commit(7)
        commit(8)
        git("checkout", "2.1")
        commit(9)
        git("checkout", "-b", "2.2", "main")
        commit(10)
        commit(11)
        git("checkout", "2.1")
        commit(12)
        tag("v2.1")
        git("checkout", "2.2")
        git("merge", "--no-ff", "2.1", "--message", "Merge 2.1->2.2")
        git("checkout", "main")
        commit(13)
        git("checkout", "2.2")
        commit(14)
        tag("v2.2")

        log()
    }

}