package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import net.nemerosa.ontrack.kdsl.spec.search.search
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * While the search documents of a type are rebuilt on one backend, the search through both says
 * that the search index is being built (#2088): the flag is the shared status of the rebuild job.
 *
 * Uses the slow testing type of the stack (`ontrack.config.search.testing.enabled`), whose rebuild
 * runs until it is stopped while a project named after [SLOW_PREFIX] exists.
 */
class ACCHASearchRebuild : AbstractACCHATestSupport() {

    @Test
    fun `the rebuild message shows through both backends while a rebuild runs on one`() {
        val project = ontrack.createProject(uid(SLOW_PREFIX), "")
        try {
            val id = status(ontrackA).id
            stopAndWait(id)
            assertEquals("TRIGGERED", ontrackA.jobs.runNow(id), "The rebuild is triggered")
            waitUntil(timeout = 30_000L, interval = 500L, task = "The rebuild runs") {
                status(ontrackB).running
            }
            listOf("A" to ontrackA, "B" to ontrackB).forEach { (name, backend) ->
                assertEquals(MESSAGE, message(backend), "Rebuild message through $name")
            }
            stopAndWait(id)
            listOf("A" to ontrackA, "B" to ontrackB).forEach { (name, backend) ->
                assertNull(message(backend), "No rebuild message through $name once the rebuild is over")
            }
        } finally {
            project.delete()
        }
    }

    private fun message(backend: Ontrack): String? =
        backend.search(query = "slow", types = listOf(TYPE)).message

    private fun stopAndWait(id: Int) {
        if (status(ontrackA).running) {
            ontrackA.jobs.stop(id)
        }
        // The backend which ran the job knows at once, the other one once Quartz removes the fire
        waitUntil(timeout = 30_000L, interval = 500L, task = "The rebuild is stopped") {
            !status(ontrackA).running && !status(ontrackB).running
        }
    }

    private fun status(ontrack: Ontrack): JobStatus =
        ontrack.jobs.status("search", "rebuild", TYPE) ?: fail("Rebuild job of $TYPE not found")

    companion object {
        /** The slow testing type of search results */
        private const val TYPE = "testing-slow"

        /** Prefix of the names of the projects making the rebuild of [TYPE] slow */
        private const val SLOW_PREFIX = "search-testing-slow-"

        private const val MESSAGE = "Search index is being built"
    }
}
