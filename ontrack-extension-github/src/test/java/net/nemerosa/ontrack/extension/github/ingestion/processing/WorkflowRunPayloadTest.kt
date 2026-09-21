package net.nemerosa.ontrack.extension.github.ingestion.processing

import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRun
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRunAction
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRunPayload
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Owner
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Repository
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.User
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.WorkflowJobStepStatus
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

class WorkflowRunPayloadTest {

    @Test
    fun `Workflow run payload JSON parsing`() {
        val createdAtDate = LocalDateTime.parse("2021-10-28T16:30:17Z", DateTimeFormatter.ISO_DATE_TIME)
        assertEquals(
            WorkflowRunPayload(
                action = WorkflowRunAction.requested,
                workflowRun = WorkflowRun(
                    id = 1,
                    name = "CI",
                    runNumber = 2,
                    pullRequests = emptyList(),
                    headBranch = "main",
                    headSha = "1234567890",
                    createdAtDate = createdAtDate,
                    updatedAtDate = null,
                    htmlUrl = "https://github.com/nemerosa/github-ingestion-poc/actions/runs/1395528922",
                    event = "push",
                    status = WorkflowJobStepStatus.in_progress,
                    conclusion = null,
                ),
                repository = Repository(
                    name = "my-repo",
                    description = "My description",
                    owner = Owner(login = "my-owner"),
                    htmlUrl = "uri:repository:my-repo",
                ),
                sender = User(login = "my-sender")
            ),
            mapOf(
                "action" to "requested",
                "workflow_run" to mapOf(
                    "id" to 1,
                    "name" to "CI",
                    "run_number" to 2,
                    "pull_requests" to emptyList<Any>(),
                    "head_branch" to "main",
                    "head_sha" to "1234567890",
                    "created_at" to "2021-10-28T16:30:17Z",
                    "updated_at" to null,
                    "html_url" to "https://github.com/nemerosa/github-ingestion-poc/actions/runs/1395528922",
                    "event" to "push",
                    "status" to "in_progress",
                    "conclusion" to null,
                ),
                "repository" to mapOf(
                    "name" to "my-repo",
                    "description" to "My description",
                    "owner" to mapOf(
                        "login" to "my-owner",
                    ),
                    "html_url" to "https://github.com/nemerosa/github-ingestion-poc",
                ),
                "sender" to mapOf(
                    "login" to "my-sender",
                ),
            ).asJson().parse()
        )
    }

}