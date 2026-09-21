package net.nemerosa.ontrack.extension.github.ingestion.ui

import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GQLRootQueryGitHubIngestionHookPayloadStatusesIT : AbstractIngestionTestSupport() {

    @Test
    fun `Getting the list of statuses`() {
        run(
            """ { gitHubIngestionHookPayloadStatuses } """
        ).let { data ->
            assertEquals(
                IngestionHookPayloadStatus.values().map { it.name }.toSet(),
                data.path("gitHubIngestionHookPayloadStatuses").values().map { it.asText() }.toSet(),
            )
        }
    }

}