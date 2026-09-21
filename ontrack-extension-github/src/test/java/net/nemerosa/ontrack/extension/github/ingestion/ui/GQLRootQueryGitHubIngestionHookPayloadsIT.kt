package net.nemerosa.ontrack.extension.github.ingestion.ui

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayload
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStatus
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals

class GQLRootQueryGitHubIngestionHookPayloadsIT : AbstractIngestionTestSupport() {

    @Test
    fun `Getting a payload by UUID`() {
        val payload = IngestionHookFixtures.sampleWorkflowRunIngestionPayload(
                message = "Sample payload",
        )
        asAdmin {
            ingestionHookPayloadStorage.store(payload, "sample source")
            run(
                    """
                    {
                     gitHubIngestionHookPayloads(uuid: "${payload.uuid}") {
                        pageItems {
                            uuid
                            timestamp
                            gitHubDelivery
                            gitHubEvent
                            gitHubHookID
                            gitHubHookInstallationTargetID
                            gitHubHookInstallationTargetType
                            payload
                            status
                            started
                            message
                            completion
                            source
                            accountName
                        }
                     }
                    }
                """
            ).let { data ->
                val item =
                        data.path("gitHubIngestionHookPayloads").path("pageItems").first().parse<IngestionHookPayload>()
                assertEquals(payload.uuid, item.uuid)
                assertEquals("sample source", item.source)
                assertEquals("admin", item.accountName)
            }
        }
    }

    @Test
    fun `Getting a payload by UUID is protected`() {
        val payload = IngestionHookFixtures.sampleWorkflowRunIngestionPayload(
                message = "Sample payload",
        )
        asAdmin {
            ingestionHookPayloadStorage.store(payload, "source")
        }
        asUser {
            runWithError(
                    """
                    {
                     gitHubIngestionHookPayloads(uuid: "${payload.uuid}") {
                        pageItems {
                            uuid
                        }
                     }
                    }
                """,
                    errorClassification = ErrorType.FORBIDDEN
            )
        }
    }

    @Test
    fun `Getting the list of payloads`() {
        testPayloads(
                expected = (40 downTo 21).toList(),
        )
    }

    @Test
    fun `Getting the list of payloads is protected`() {
        testPayloads(
                expected = (40 downTo 21).toList(),
                security = { code ->
                    asUser {
                        code()
                    }
                },
                expectingError = ErrorType.FORBIDDEN
        )
    }

    @Test
    fun `Paginating the list of payloads`() {
        testPayloads(
                offset = 20,
                size = 10,
                expected = (20 downTo 11).toList(),
        )
    }

    @Test
    fun `Filtering the list of payloads on a status`() {
        testPayloads(
                size = 5,
                statuses = listOf(IngestionHookPayloadStatus.ERRORED),
                expected = listOf(39, 35, 31, 27, 23)
        )
    }

    @Test
    fun `Filtering the list of payloads on several statuses`() {
        testPayloads(
                size = 5,
                statuses = listOf(IngestionHookPayloadStatus.ERRORED, IngestionHookPayloadStatus.COMPLETED),
                expected = listOf(39, 38, 35, 34, 31)
        )
    }

    @Test
    fun `Filtering the list of payloads on the repository`() {
        testPayloads(
                repository = "repository-0",
                expected = listOf(2, 1)
        )
    }

    @Test
    fun `Filtering the list of payloads on the owner`() {
        testPayloads(
                owner = "owner-0",
                expected = listOf(4, 3, 2, 1)
        )
    }

    private fun testPayloads(
            offset: Int? = null,
            size: Int? = null,
            statuses: List<IngestionHookPayloadStatus>? = null,
            repository: String? = null,
            owner: String? = null,
            expected: List<Int>,
            expectingError: ErrorType? = null,
            security: (code: () -> Unit) -> Unit = { code -> asAdmin { code() } },
    ) {
        asAdmin {
            createPayloads()
        }
        security {
            val query = """
                query Payloads(
                    ${'$'}offset: Int,
                    ${'$'}size: Int,
                    ${'$'}statuses: [IngestionHookPayloadStatus!],
                    ${'$'}repository: String,
                    ${'$'}owner: String,
                ) {
                    gitHubIngestionHookPayloads(
                        offset: ${'$'}offset,
                        size: ${'$'}size,
                        statuses: ${'$'}statuses,
                        repository: ${'$'}repository,
                        owner: ${'$'}owner,
                    ) {
                        pageItems {
                            message
                        }
                    }
                }
            """
            val variables = mapOf(
                    "offset" to offset,
                    "size" to size,
                    "statuses" to statuses,
                    "repository" to repository,
                    "owner" to owner,
            )

            if (expectingError != null) {
                runWithError(query, variables, errorClassification = expectingError)
            } else {
                run(
                        query, variables
                ).let { data ->
                    assertEquals(
                            expected.map { no ->
                                "Payload #$no"
                            },
                            data.path("gitHubIngestionHookPayloads").path("pageItems").values().map { node ->
                                node.getRequiredTextField("message")
                            }
                    )
                }
            }
        }
    }

    private fun createPayloads() {
        val ref = Time.now()
        ingestionHookPayloadStorage.cleanUntil(ref)
        val payloads = (1..40).map { no ->
            val status = when (no % 4) {
                0 -> IngestionHookPayloadStatus.SCHEDULED
                1 -> IngestionHookPayloadStatus.PROCESSING
                2 -> IngestionHookPayloadStatus.COMPLETED
                else -> IngestionHookPayloadStatus.ERRORED
            }
            IngestionHookFixtures.sampleWorkflowRunIngestionPayload(
                    repoName = "repository-${no / 3}",
                    owner = "owner-${no / 5}",
                    timestamp = ref.minusDays(1).plusSeconds(no.toLong()),
                    message = "Payload #$no",
                    status = status,
            )
        }
        payloads.forEach { payload ->
            ingestionHookPayloadStorage.store(payload, "source")
        }
    }

}