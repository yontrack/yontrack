package net.nemerosa.ontrack.git

import net.nemerosa.ontrack.git.exceptions.GitRepositoryPushRejectedException
import net.nemerosa.ontrack.git.support.GitRepo
import org.junit.jupiter.api.Test
import java.io.File
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Merging a branch when the remote base branch moves between the fetch and the push.
 *
 * The move is made by a `pre-push` hook of the clone, which JGit runs after having read the
 * remote refs and before sending the update.
 */
class GitRepositoryClientMergeBranchTest {

    @Test
    fun `Base branch moving once between fetch and push is merged again and pushed`() {
        withMovingBase(oneShot = true) { client, origin ->
            val commit = client.mergeBranch(head = "feature", base = "main")

            val originMain = origin.git("log", "--pretty=format:%s", "main").lines()
            assertTrue("Commit 2" in originMain, "Feature commit is on the remote base branch")
            assertTrue("Concurrent commit" in originMain, "Concurrent commit is kept on the remote base branch")
            assertEquals(origin.git("rev-parse", "main").trim(), commit.id, "Returned commit is the remote head")
        }
    }

    @Test
    fun `Base branch moving at every push fails the merge`() {
        withMovingBase(oneShot = false) { client, origin ->
            val exception = assertFailsWith<GitRepositoryPushRejectedException> {
                client.mergeBranch(head = "feature", base = "main")
            }
            assertTrue("main" in (exception.message ?: ""), "Message names the base branch")

            val originMain = origin.git("log", "--pretty=format:%s", "main").lines()
            assertFalse("Commit 2" in originMain, "Feature commit is not on the remote base branch")
        }
    }

    private fun withMovingBase(
        oneShot: Boolean,
        code: (client: GitRepositoryClient, origin: GitRepo) -> Unit,
    ) {
        GitRepo.prepare {
            gitInit()
            git("checkout", "-b", "main")
            commit(1)
            git("checkout", "-b", "feature")
            commit(2)
            git("checkout", "main")
            // The remote accepts pushes on main
            git("checkout", "--detach")
        } withClone { client, clientRepo, origin ->
            client.sync(Consumer { println(it) })
            val hook = File(clientRepo.dir, ".git/hooks/pre-push")
            hook.parentFile.mkdirs()
            hook.writeText(
                """
                |#!/bin/sh
                |unset GIT_DIR GIT_INDEX_FILE GIT_WORK_TREE
                |${if (oneShot) "rm -f \"$0\"" else ""}
                |cd "${origin.dir.absolutePath}"
                |git checkout -q main
                |echo "${'$'}${'$'}" >> concurrent
                |git add concurrent
                |git commit -q -m "Concurrent commit"
                |git checkout -q --detach
                |""".trimMargin()
            )
            hook.setExecutable(true)
            code(client, origin)
        }
    }
}
