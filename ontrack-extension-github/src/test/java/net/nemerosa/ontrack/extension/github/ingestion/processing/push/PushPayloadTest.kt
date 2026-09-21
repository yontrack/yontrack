package net.nemerosa.ontrack.extension.github.ingestion.processing.push

import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Commit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PushPayloadTest {

    @Test
    fun `Tag push payload`() {
        assertEquals(null, payload().getTag())
        assertEquals("1.0", payload(ref = "refs/tags/1.0").getTag())
    }

    @Test
    fun `Checking the paths`() {
        assertEquals(true, payload().isAdded("added-path-in-commit-1"))
        assertEquals(true, payload().isAddedOrModified("added-path-in-commit-1"))

        assertEquals(true, payload().isModified("modified-path-in-commit-1"))
        assertEquals(true, payload().isAddedOrModified("modified-path-in-commit-1"))

        assertEquals(true, payload().isRemoved("removed-path-in-commit-1"))

        assertEquals(true, payload().isAdded("added-path-in-commit-2"))
        assertEquals(true, payload().isAddedOrModified("added-path-in-commit-2"))

        assertEquals(true, payload().isModified("modified-path-in-commit-2"))
        assertEquals(true, payload().isAddedOrModified("modified-path-in-commit-2"))

        assertEquals(true, payload().isRemoved("removed-path-in-commit-2"))
    }

    private fun payload(
        ref: String = "refs/heads/${IngestionHookFixtures.sampleBranch}",
    ) = PushPayload(
        repository = IngestionHookFixtures.sampleRepository(),
        ref = ref,
        commits = listOf(
            Commit(
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
            Commit(
                id = "commit-2",
                message = "Commit 2",
                author = IngestionHookFixtures.sampleAuthor(),
                added = listOf(
                    "added-path-in-commit-2",
                ),
                modified = listOf(
                    "modified-path-in-commit-2",
                ),
                removed = listOf(
                    "removed-path-in-commit-2",
                ),
            ),
        ),
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