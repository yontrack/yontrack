package net.nemerosa.ontrack.extension.github.ingestion.processing.config

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayloadListenerCheck
import net.nemerosa.ontrack.extension.github.ingestion.support.IngestionModelAccessService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IngestionPushPayloadListenerTest {

    private lateinit var configService: ConfigService
    private lateinit var ingestionModelAccessService: IngestionModelAccessService
    private lateinit var listener: IngestionPushPayloadListener

    private lateinit var branch: Branch

    @BeforeEach
    fun before() {
        configService = mockk(relaxed = true)
        ingestionModelAccessService = mockk()
        listener = IngestionPushPayloadListener(configService, ingestionModelAccessService)

        branch = Branch.of(
            Project.of(NameDescription.nd("project", "")),
            NameDescription.nd("main", ""),
        )
        every {
            ingestionModelAccessService.getOrCreateProject(IngestionHookFixtures.sampleRepository(), null)
        } returns branch.project
        every {
            ingestionModelAccessService.getOrCreateBranch(branch.project, IngestionHookFixtures.sampleBranch, null)
        } returns branch
    }

    @Test
    fun `Processing when config is added`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            added = listOf(INGESTION_CONFIG_FILE_PATH)
        )
        listener.process(payload, null)
        verify {
            configService.loadAndSaveConfig(
                branch = branch,
                path = INGESTION_CONFIG_FILE_PATH,
            )
        }
    }

    @Test
    fun `Processing when config is modified`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            modified = listOf(INGESTION_CONFIG_FILE_PATH)
        )
        listener.process(payload, null)
        verify {
            configService.loadAndSaveConfig(
                branch = branch,
                path = INGESTION_CONFIG_FILE_PATH,
            )
        }
    }

    @Test
    fun `Processing when config is deleted`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            removed = listOf(INGESTION_CONFIG_FILE_PATH)
        )
        listener.process(payload, null)
        verify {
            configService.removeConfig(
                branch = branch,
            )
        }
    }

    @Test
    fun `Ignoring when path is not included at all`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            added = listOf("any/other/path"),
            modified = listOf("any/other/path"),
            removed = listOf("any/other/path"),
        )
        listener.process(payload, null)
        verify {
            configService wasNot Called
        }
    }

    @Test
    fun `Processing check when config is added`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            added = listOf(INGESTION_CONFIG_FILE_PATH)
        )
        assertEquals(
            PushPayloadListenerCheck.TO_BE_PROCESSED,
            listener.preProcessCheck(payload)
        )
    }

    @Test
    fun `Processing check when config is modified`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            modified = listOf(INGESTION_CONFIG_FILE_PATH)
        )
        assertEquals(
            PushPayloadListenerCheck.TO_BE_PROCESSED,
            listener.preProcessCheck(payload)
        )
    }

    @Test
    fun `Processing check when config is deleted`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            removed = listOf(INGESTION_CONFIG_FILE_PATH)
        )
        assertEquals(
            PushPayloadListenerCheck.TO_BE_PROCESSED,
            listener.preProcessCheck(payload)
        )
    }

    @Test
    fun `Ignoring check when path is not included at all`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            added = listOf("any/other/path"),
            modified = listOf("any/other/path"),
            removed = listOf("any/other/path"),
        )
        assertEquals(
            PushPayloadListenerCheck.IGNORED,
            listener.preProcessCheck(payload)
        )
    }

    @Test
    fun `Ignoring check for tags`() {
        val payload = IngestionHookFixtures.samplePushPayload(
            ref = "refs/tags/v0.1.0",
            added = listOf(INGESTION_CONFIG_FILE_PATH),
        )
        assertEquals(
            PushPayloadListenerCheck.IGNORED,
            listener.preProcessCheck(payload)
        )
    }

}