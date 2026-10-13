package net.nemerosa.ontrack.extension.av.tracking

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.sourceConfig
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [AutoVersioningTrackingStoreMigration] moves the trails of the `AutoVersioningTracking` entity
 * store into their table, and removes each of them from the store once copied (#2087).
 */
@AsAdminTest
class AutoVersioningTrackingStoreMigrationIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var migration: AutoVersioningTrackingStoreMigration

    @Autowired
    private lateinit var avTrailRepository: AvTrailRepository

    @Test
    fun `Every stored trail is moved into the table`() {
        withRuns(2) { runs ->
            runs.forEach { storeTrail(it, validTrail()) }

            migration.start()

            runs.forEach { run ->
                assertNotNull(avTrailRepository.findByPromotionRun(run, ALL), "Trail of run ${run.id} copied")
                assertEquals(0, storedTrails(run), "Trail of run ${run.id} removed from the store")
            }
        }
    }

    @Test
    fun `A trail which cannot be copied stays in the store with the ones not copied yet`() {
        withRuns(3) { (copied, failing, notYetCopied) ->
            storeTrail(copied, validTrail())
            storeTrail(failing, """{"branches": "not a list"}""")
            storeTrail(notYetCopied, validTrail())

            assertFails { migration.start() }

            assertNotNull(avTrailRepository.findByPromotionRun(copied, ALL), "First trail copied")
            assertEquals(0, storedTrails(copied), "First trail removed from the store")
            assertEquals(1, storedTrails(failing), "Trail which cannot be copied kept in the store")
            assertNull(avTrailRepository.findByPromotionRun(notYetCopied, ALL), "Last trail not copied")
            assertEquals(1, storedTrails(notYetCopied), "Trail not copied yet kept in the store")
        }
    }

    private fun validTrail(): String = StoredAutoVersioningTrail(
        branches = listOf(
            StoredBranchTrail(
                id = uid("t-"),
                project = uid("P"),
                branch = "main",
                configuration = sourceConfig(),
                rejectionReason = null,
                orderId = null,
            )
        )
    ).asJson().toString()

    private fun storeTrail(run: PromotionRun, data: String) {
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO ENTITY_STORE (PROMOTION_RUN, STORE, NAME, DATA)
                VALUES (:run, :store, :name, CAST(:data AS JSONB))
            """,
            mapOf("run" to run.id(), "store" to STORE, "name" to "trail", "data" to data)
        )
    }

    private fun storedTrails(run: PromotionRun): Int =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ENTITY_STORE WHERE STORE = :store AND PROMOTION_RUN = :run",
            mapOf("run" to run.id(), "store" to STORE),
            Int::class.java
        ) ?: 0

    private fun withRuns(count: Int, code: (runs: List<PromotionRun>) -> Unit) {
        project {
            branch {
                val pl = promotionLevel()
                val runs = (1..count).map {
                    build { }.let { build -> build.promote(pl) }
                }
                code(runs)
            }
        }
    }

    companion object {
        private const val STORE = "AutoVersioningTracking"
        private val ALL = AutoVersioningTrailFilter(onlyEligible = false)
    }
}
