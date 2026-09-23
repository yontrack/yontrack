package net.nemerosa.ontrack.extension.findings.ingestion

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.findings.license.FindingsLicense
import net.nemerosa.ontrack.extension.findings.license.FindingsNativeFormatsLicenseException
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.report.FindingsReportParser
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.search.FindingSearchIndexer
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.RunInfoService
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class FindingsIngestionServiceImplTest {

    private val nativeParser = mockk<FindingsReportParser> {
        every { format } returns "sarif"
        every { nativeFormat } returns true
    }
    private val structureService = mockk<StructureService>()
    private val runInfoService = mockk<RunInfoService>()
    private val findingRepository = mockk<FindingRepository>()
    private val findingStateService = mockk<FindingStateService>()
    private val eventPostService = mockk<EventPostService>()
    private val findingsLicense = mockk<FindingsLicense>()
    private val findingSearchIndexer = mockk<FindingSearchIndexer>()
    private val meterRegistry = SimpleMeterRegistry()

    private val service = FindingsIngestionServiceImpl(
        parsers = listOf(nativeParser),
        structureService = structureService,
        runInfoService = runInfoService,
        findingRepository = findingRepository,
        findingStateService = findingStateService,
        eventPostService = eventPostService,
        findingsLicense = findingsLicense,
        findingSearchIndexer = findingSearchIndexer,
        meterRegistry = meterRegistry,
        securityService = mockk(),
    )

    @Test
    fun `The licence of a native format is checked before the report is read and anything is written`() {
        every { findingsLicense.checkNativeFormat("sarif") } throws FindingsNativeFormatsLicenseException("sarif")
        assertThrows<FindingsNativeFormatsLicenseException> {
            service.ingest(
                build = mockk<Build>(),
                request = FindingsIngestionRequest(
                    validation = "security",
                    format = "sarif",
                    kind = FindingKind.CODE,
                    report = """{"version": "2.1.0", "runs": []}""".parseAsJson(),
                ),
            )
        }
        verify(exactly = 0) { nativeParser.parse(any(), any(), any()) }
        verify { structureService wasNot Called }
        verify { runInfoService wasNot Called }
        verify { findingRepository wasNot Called }
        verify { eventPostService wasNot Called }
        verify { findingSearchIndexer wasNot Called }
        // A rejected report is not measured
        assertTrue(meterRegistry.meters.isEmpty())
    }
}
