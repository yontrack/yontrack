package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.service.ScorecardService
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The recomputes run as jobs, in their own transactions: the data of the tests must be committed,
 * and removed at the end.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EstateRecomputeIT : EstatesTestSupport() {

    @Autowired
    private lateinit var scorecardService: ScorecardService

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    @Test
    @AsAdminTest
    fun `Recomputing an estate, and a project for every set it is in`() {
        val label = asAdmin { label() }
        val selected = asAdmin {
            project().apply {
                labels = listOf(label)
                branch("main") {
                    val gold = promotionLevel("GOLD")
                    build().promote(gold)
                }
            }
        }
        val estate = estate(label, marker = EstatePromotionMarker("GOLD"))
        try {
            val today = Time.now.toLocalDate()

            // Estate recompute, as a creator
            asGlobalRole(Roles.GLOBAL_CREATOR) {
                assertNotNull(estateService.recompute(estate), "Recompute queued").get(30, TimeUnit.SECONDS)
            }
            asAdmin {
                val readings = readingRepository.findLatestByProject(selected.id())
                assertTrue(readings.none { it.estateId == null }, "Only the estate is recomputed")
                assertEquals(
                    ReadingKeys.DELIVERY_FREQUENCY,
                    readings.single { it.estateId == estate.id && it.key == ReadingKeys.DELIVERY_FREQUENCY && it.day == today }.key
                )
            }

            // Project recompute: every set
            asAdmin {
                assertNotNull(scorecardService.recompute(selected), "Recompute queued").get(30, TimeUnit.SECONDS)
                val readings = readingRepository.findLatestByProject(selected.id()).filter { it.day == today }
                assertEquals(
                    setOf(null, estate.id),
                    readings.map { it.estateId }.toSet()
                )
            }
        } finally {
            asAdmin {
                estateService.delete(estate.id)
                structureService.deleteProject(selected.id)
                labelManagementService.deleteLabel(label.id)
            }
        }
    }
}
