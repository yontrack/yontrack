package net.nemerosa.ontrack.extension.scorecard.export

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.metrics.MetricsExportService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReadingsExporterTest {

    private val project = Project.of(NameDescription.nd("P", "")).withId(ID.of(1))

    private val computedAt = LocalDateTime.of(2026, 9, 28, 2, 0)

    private val reading = Reading(
        estateId = null,
        projectId = 1,
        key = ReadingKeys.DELIVERY_FREQUENCY,
        day = computedAt.toLocalDate(),
        computedAt = computedAt,
        windowStart = computedAt.minusDays(90),
        windowEnd = computedAt,
        value = 2.5,
        basis = ReadingBasis.MEASURED,
        unknownReason = null,
        details = emptyMap<String, Any>().asJson(),
    )

    @Test
    fun `The readings are exported in one batch`() {
        val metricsExportService = mockk<MetricsExportService>(relaxed = true)
        ReadingsExporter(metricsExportService).onReadings(NoEstateReadingSet, project, listOf(reading))
        verify(exactly = 1) {
            metricsExportService.batchExportMetrics(listOf(ReadingMetrics.metric("-", "P", reading)))
        }
    }

    @Test
    fun `An export which fails does not fail the computation`() {
        val metricsExportService = mockk<MetricsExportService>()
        every { metricsExportService.batchExportMetrics(any()) } throws RuntimeException("Backend down")
        // Does not throw
        ReadingsExporter(metricsExportService).onReadings(NoEstateReadingSet, project, listOf(reading))
    }
}
