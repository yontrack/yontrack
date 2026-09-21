package net.nemerosa.ontrack.extension.github.ingestion.processing.config

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.ingestion.FileLoaderService
import net.nemerosa.ontrack.extension.github.ingestion.config.parser.ConfigParsingException
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultConfigLoaderServiceTest {

    @Test
    fun `Project not configured returns null`() {
        test(
            projectConfigured = false,
            expectedConfig = false,
        )
    }

    @Test
    fun `Branch not configured returns null`() {
        test(
            branchConfigured = false,
            expectedConfig = false,
        )
    }

    @Test
    fun `Path not found returns null`() {
        test(
            pathFound = false,
            expectedConfig = false,
        )
    }

    @Test
    fun `Loading the configuration`() {
        test()
    }

    @Test
    fun `Configuration parsing error returns an error`() {
        assertFailsWith<ConfigParsingException> {
            test(
                incorrectConfig = true,
                expectedConfig = false,
            )
        }
    }

    private fun test(
        projectConfigured: Boolean = true,
        branchConfigured: Boolean = true,
        pathFound: Boolean = true,
        incorrectConfig: Boolean = false,
        expectedConfig: Boolean = true,
    ) {
        val project = Project.of(NameDescription.nd("test", ""))
        val branch = Branch.of(project, NameDescription.nd("main", ""))

        val fileLoaderService = mockk<FileLoaderService>()

        if (pathFound && branchConfigured && projectConfigured) {
            val path = if (incorrectConfig) {
                "/ingestion/config-incorrect.yml"
            } else {
                "/ingestion/config.yml"
            }
            every {
                fileLoaderService.loadFile(branch, INGESTION_CONFIG_FILE_PATH)
            } returns TestUtils.resourceString(path)
        } else {
            every {
                fileLoaderService.loadFile(branch, INGESTION_CONFIG_FILE_PATH)
            } returns null
        }

        val configLoaderService = DefaultConfigLoaderService(
            fileLoaderService = fileLoaderService,
        )
        val config = configLoaderService.loadConfig(branch, INGESTION_CONFIG_FILE_PATH)
        if (expectedConfig) {
            assertNotNull(config, "Configuration was loaded")
        } else {
            assertNull(config, "Configuration could not be found")
        }
    }

}