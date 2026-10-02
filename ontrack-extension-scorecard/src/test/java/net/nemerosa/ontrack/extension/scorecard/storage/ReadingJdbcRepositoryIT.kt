package net.nemerosa.ontrack.extension.scorecard.storage

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.estates.EstateSecurity
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingJdbcRepositoryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    @Autowired
    private lateinit var estateRepository: EstateRepository

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
    fun `Every snapshot is read page by page`() {
        asAdmin {
            project {
                val readings = (1..5).map { reading(day = today.minusDays(it.toLong()), value = it.toDouble()) }
                readingRepository.save(readings)
                val pages = mutableListOf<List<Reading>>()
                readingRepository.forEachPage(estates = true, pageSize = 2) { page -> pages += page }
                assertTrue(pages.all { it.size <= 2 }, "Pages are at most of the given size")
                val own = pages.flatten().filter { it.projectId == id() }
                assertEquals(readings.sortedBy { it.day }, own.sortedBy { it.day }, "Every snapshot is read once")
            }
        }
    }

    @Test
    fun `Reading every snapshot without the estates`() {
        asAdmin {
            project {
                val estateId = estateRepository.create(
                    name = uid("E"),
                    description = null,
                    labelIds = emptyList(),
                    marker = null,
                    readingConfigs = emptyList(),
                    security = EstateSecurity(),
                )
                try {
                    readingRepository.save(listOf(reading(value = 1.0), reading(value = 2.0, estateId = estateId)))

                    fun read(estates: Boolean): List<Double?> {
                        val values = mutableListOf<Double?>()
                        readingRepository.forEachPage(estates = estates, pageSize = 100) { page ->
                            values += page.filter { it.projectId == id() }.map { it.value }
                        }
                        return values.sortedBy { it }
                    }

                    assertEquals(listOf(1.0, 2.0), read(estates = true))
                    assertEquals(listOf(1.0), read(estates = false))
                } finally {
                    estateRepository.delete(estateId)
                }
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
