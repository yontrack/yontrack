package net.nemerosa.ontrack.extension.github.ingestion.processing.push

import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Commit
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.tagging.TagPushPayloadListener
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TagPushPayloadListenerTest {

    private lateinit var listener: TagPushPayloadListener

    @BeforeEach
    fun before() {
        listener = TagPushPayloadListener(
            propertyService = mockk(),
            ingestionModelAccessService = mockk(),
            configService = mockk(),
            taggingStrategyRegistry = mockk(),
            commitPropertyTaggingStrategy = mockk(),
        )
    }

    @Test
    fun `Processing only for tags`() {
        assertEquals(
            PushPayloadListenerCheck.IGNORED,
            listener.preProcessCheck(
                payload(
                    ref = "refs/heads/main"
                )
            )
        )
        assertEquals(
            PushPayloadListenerCheck.TO_BE_PROCESSED,
            listener.preProcessCheck(
                payload(
                    ref = "refs/tags/1.0"
                )
            )
        )
    }

    private fun payload(
        ref: String,
    ) = PushPayload(
        repository = IngestionHookFixtures.sampleRepository(),
        ref = ref,
        headCommit = Commit(
            id = "commit-1",
            message = "Commit 1",
            author = IngestionHookFixtures.sampleAuthor(),
            added = listOf(
                "added-path-in-commit-1",
            ),
            modified = listOf(
                "modified-path-in-commit-1",
            ),
            removed = listOf(
                "removed-path-in-commit-1",
            ),
        ),
    )

}