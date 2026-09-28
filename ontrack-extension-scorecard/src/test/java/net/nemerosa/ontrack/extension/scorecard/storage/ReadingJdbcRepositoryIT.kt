package net.nemerosa.ontrack.extension.scorecard.storage

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingJdbcRepositoryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    private val today: LocalDate = Time.now.toLocalDate()

    private fun Project.reading(
        key: String = "delivery.leadTime",
        day: LocalDate = today,
        value: Double? = 10.0,
        estateId: Int? = null,
    ): Reading {
        val computedAt = day.atTime(2, 0)
        return Reading(
            estateId = estateId,
            projectId = id(),
            key = key,
            day = day,
            computedAt = computedAt,
            windowStart = computedAt.minusDays(90),
            windowEnd = computedAt,
            value = value,
            basis = if (value == null) ReadingBasis.UNKNOWN else ReadingBasis.MEASURED,
            unknownReason = if (value == null) ReadingUnknownReason.NO_SAMPLES else null,
            details = mapOf("count" to 3).asJson(),
        )
    }

    @Test
    fun `Saving and reading back a snapshot`() {
        asAdmin {
            project {
                val reading = reading()
                readingRepository.save(listOf(reading))
                assertEquals(listOf(reading), readingRepository.findLatestByProject(id()))
            }
        }
    }

    @Test
    fun `Saving an unknown reading`() {
        asAdmin {
            project {
                val reading = reading(value = null)
                readingRepository.save(listOf(reading))
                val stored = readingRepository.findLatestByProject(id()).single()
                assertNull(stored.value)
                assertEquals(ReadingBasis.UNKNOWN, stored.basis)
                assertEquals(ReadingUnknownReason.NO_SAMPLES, stored.unknownReason)
            }
        }
    }

    @Test
    fun `A snapshot of the same day with no estate is overwritten`() {
        asAdmin {
            project {
                readingRepository.save(listOf(reading(value = 10.0)))
                readingRepository.save(listOf(reading(value = 20.0)))
                val history = readingRepository.findHistory(null, id(), "delivery.leadTime", today.minusDays(10))
                assertEquals(listOf(20.0), history.map { it.value })
            }
        }
    }

    @Test
    fun `Daily snapshots are appended`() {
        asAdmin {
            project {
                readingRepository.save(listOf(reading(day = today.minusDays(2), value = 1.0)))
                readingRepository.save(listOf(reading(day = today.minusDays(1), value = 2.0)))
                readingRepository.save(listOf(reading(day = today, value = 3.0)))
                readingRepository.save(listOf(reading(key = "delivery.frequency", day = today.minusDays(1), value = 4.0)))

                assertEquals(
                    listOf(2.0, 3.0),
                    readingRepository.findHistory(null, id(), "delivery.leadTime", today.minusDays(1)).map { it.value }
                )
                assertEquals(
                    mapOf("delivery.leadTime" to 3.0, "delivery.frequency" to 4.0),
                    readingRepository.findLatestByProject(id()).associate { it.key to it.value }
                )
            }
        }
    }

    @Test
    fun `Snapshots before a day are purged`() {
        asAdmin {
            project {
                readingRepository.save(listOf(reading(day = today.minusDays(800), value = 1.0)))
                readingRepository.save(listOf(reading(day = today.minusDays(730), value = 2.0)))
                readingRepository.save(listOf(reading(day = today, value = 3.0)))
                val count = readingRepository.deleteBefore(today.minusDays(730))
                assertTrue(count >= 1)
                assertEquals(
                    listOf(2.0, 3.0),
                    readingRepository.findHistory(null, id(), "delivery.leadTime", today.minusDays(1000)).map { it.value }
                )
            }
        }
    }

    @Test
    fun `Snapshots go with their project`() {
        asAdmin {
            val project = project()
            readingRepository.save(listOf(project.reading()))
            structureService.deleteProject(project.id)
            assertEquals(emptyList(), readingRepository.findLatestByProject(project.id()))
        }
    }
}
