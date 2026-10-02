package net.nemerosa.ontrack.extension.audittrail.service

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Appending in the transactions of the callers: the data of the tests must be committed, and
 * removed at the end.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrailServiceTransactionsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val ci: JsonNode = mapOf("account" to "ci-bot", "via" to "token", "tokenName" to "ci-demo").asJson()

    @Test
    fun `Concurrent appends to the trail of one build never fork it`() {
        withBuild { build ->
            trailService.append(build, TrailEntryTypes.BUILD_CREATED, mapOf("build" to mapOf("id" to build.id())).asJson(), ci)

            val threads = 8
            val appendsPerThread = 10
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(threads)
            try {
                val futures = (1..threads).map { thread ->
                    executor.submit {
                        start.await()
                        repeat(appendsPerThread) { index ->
                            trailService.append(
                                build,
                                "validation.run",
                                mapOf("thread" to thread, "index" to index).asJson(),
                                ci
                            )
                        }
                    }
                }
                start.countDown()
                futures.forEach { it.get(60, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }

            val entries = trailService.getEntries(build)
            assertEquals((1..(1 + threads * appendsPerThread)).toList(), entries.map { it.seq }, "Contiguous seq")
            assertNull(entries.first().prevHash)
            entries.zipWithNext().forEach { (previous, next) ->
                assertEquals(previous.hash, next.prevHash, "Seq ${next.seq} chained to seq ${previous.seq}")
            }
            assertEquals(entries.size, entries.map { it.hash }.toSet().size, "No two entries share a hash")
        }
    }

    @Test
    fun `An entry is written in the transaction of its caller, and rolled back with it`() {
        withBuild { build ->
            TransactionTemplate(transactionManager).executeWithoutResult { status ->
                trailService.append(build, TrailEntryTypes.BUILD_CREATED, mapOf("build" to mapOf("id" to build.id())).asJson(), ci)
                assertEquals(1, trailService.getEntries(build).size, "Visible in the transaction")
                status.setRollbackOnly()
            }
            assertTrue(trailService.getEntries(build).isEmpty(), "Rolled back with the caller")
        }
    }

    private fun withBuild(code: (Build) -> Unit) {
        val project = asAdmin { project() }
        try {
            val build = asAdmin {
                var build: Build? = null
                project.branch { build = build() }
                build!!
            }
            asAdmin { code(build) }
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }
}
