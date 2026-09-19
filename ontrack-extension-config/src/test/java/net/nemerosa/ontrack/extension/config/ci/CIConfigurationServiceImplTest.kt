package net.nemerosa.ontrack.extension.config.ci

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.config.ci.conditions.ConditionRegistry
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngineRegistry
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtensionService
import net.nemerosa.ontrack.extension.config.model.CoreConfigurationService
import net.nemerosa.ontrack.extension.config.scm.SCMEngineRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CIConfigurationServiceImplTest {

    private val ciConfigurationParser = mockk<CIConfigurationParser>()

    private val service = CIConfigurationServiceImpl(
        ciConfigurationParser = ciConfigurationParser,
        ciEngineRegistry = mockk<CIEngineRegistry>(),
        scmEngineRegistry = mockk<SCMEngineRegistry>(),
        conditionRegistry = mockk<ConditionRegistry>(),
        coreConfigurationService = mockk<CoreConfigurationService>(),
        ciConfigExtensionService = mockk<CIConfigExtensionService>(),
        meterRegistry = SimpleMeterRegistry(),
    )

    /**
     * A GitLab API error carries the URL-encoded project path, whose `%2F` used to make the
     * wrapping exception re-format the message and throw `UnknownFormatConversionException`,
     * losing the real cause altogether. See issue #1837.
     */
    @Test
    fun `a cause whose message carries a percent is surfaced verbatim`() {
        val cause = RuntimeException(
            "401 Unauthorized on GET https://gitlab.com/api/v4/projects/nemerosa%2Fyontrack/repository/branches/main"
        )
        every { ciConfigurationParser.parseConfig(any()) } throws cause

        val ex = assertFailsWith<CIConfigGeneralException> {
            service.configureBuild(config = "---", ci = null, scm = null, env = emptyList())
        }

        assertEquals(
            "Unexpected error while injecting the configuration: " +
                    "401 Unauthorized on GET https://gitlab.com/api/v4/projects/nemerosa%2Fyontrack/repository/branches/main",
            ex.message,
        )
        assertSame(cause, ex.cause)
    }
}
