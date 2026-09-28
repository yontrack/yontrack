package net.nemerosa.ontrack.extension.scorecard.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.service.ScorecardService
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * The recompute runs as a job, in its own transactions: the data of the tests must be committed,
 * and the projects are deleted at the end.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScorecardRecomputeIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var scorecardService: ScorecardService

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    private fun withProject(code: (Project) -> Unit) {
        val project = asAdmin {
            project().apply {
                branch("main") {
                    val gold = promotionLevel("GOLD")
                    build().promote(gold)
                }
            }
        }
        try {
            code(project)
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

    @Test
    fun `Recompute overwrites the snapshot of the day`() {
        withProject { project ->
            val today = Time.now.toLocalDate()
            asAdmin {
                readingRepository.save(
                    listOf(
                        Reading(
                            estateId = null,
                            projectId = project.id(),
                            key = ReadingKeys.DELIVERY_FREQUENCY,
                            day = today,
                            computedAt = today.atStartOfDay(),
                            windowStart = today.atStartOfDay().minusDays(90),
                            windowEnd = today.atStartOfDay(),
                            value = 999.0,
                            basis = ReadingBasis.MEASURED,
                            unknownReason = null,
                            details = mapOf("count" to 999).asJson(),
                        )
                    )
                )
            }
            asAdmin {
                project.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                    val future = scorecardService.recompute(project)
                    assertNotNull(future, "Recompute queued").get(30, TimeUnit.SECONDS)
                }
            }
            val frequency = readingRepository.findHistory(null, project.id(), ReadingKeys.DELIVERY_FREQUENCY, today)
                .single()
            assertEquals(1, frequency.details.path("count").asInt())
            assertEquals(7.0 / 90.0, frequency.value!!, 1e-9)
        }
    }

    @Test
    fun `Recompute needs the configuration of the project`() {
        withProject { project ->
            asAdmin {
                project.asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                    assertFailsWith<AccessDeniedException> {
                        scorecardService.recompute(project)
                    }
                }
            }
        }
    }
}
