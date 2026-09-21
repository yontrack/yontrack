package net.nemerosa.ontrack.kdsl.acceptance.tests.tfc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.queue.QueueACCTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.resourceAsText
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.parse
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.queue.QueueRecordState
import net.nemerosa.ontrack.kdsl.spec.extension.queue.queue
import net.nemerosa.ontrack.kdsl.spec.extension.tfc.TFCConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.tfc.TFCSettings
import net.nemerosa.ontrack.kdsl.spec.extension.tfc.tfc
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ACCTFCValidation : AbstractACCDSLTestSupport() {

    @Test
    fun `Validation without variables`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    // Payload
                    val payload =
                        resourceAsText("/tfc/run-completed-applied.json")
                            .parseAsJson()
                    // Sending the payload to the hook
                    withTfcHookEnabled {
                        withTfcConfiguration {
                            val response = sendPayloadToHook(
                                ref = this,
                                validation = vs.name,
                                payload = payload,
                            )
                            // Gets the queue ID
                            val queueID = response.queueID
                            // Waiting for the processing to be don
                            val queueSupport = QueueACCTestSupport(ontrack)
                            queueSupport.waitForQueueRecordToBe(queueID, QueueRecordState.COMPLETED)
                            // Checks the build has been validated to "passed"
                            val run = getValidationRuns(vs.name, 1).firstOrNull()
                            assertNotNull(run, "Validation was done") {
                                assertEquals("PASSED", it.lastStatus.id)
                            }
                            // Checks the queue source
                            assertNotNull(
                                ontrack.queue.findQueueRecordByID(queueID),
                                "Queue record available"
                            ) { record ->
                                assertNotNull(record.source, "Queue record source available") { source ->
                                    assertEquals("hook", source.feature)
                                    assertEquals("hook", source.id)
                                    assertEquals("tfc", source.data.getRequiredTextField("hook"))
                                    assertTrue(
                                        source.data.getRequiredTextField("id").isNotBlank(),
                                        "Hook record ID is set"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Failed validation on failed run`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    // Payload
                    val payload =
                        resourceAsText("/tfc/run-errored.json")
                            .parseAsJson()
                    // Sending the payload to the hook
                    withTfcHookEnabled {
                        withTfcConfiguration {
                            val response = sendPayloadToHook(
                                ref = this,
                                validation = vs.name,
                                payload = payload,
                            )
                            // Gets the queue ID
                            val queueID = response.queueID
                            // Waiting for the processing to be don
                            val queueSupport = QueueACCTestSupport(ontrack)
                            queueSupport.waitForQueueRecordToBe(queueID, QueueRecordState.COMPLETED)
                            // Checks the build has been validated to "passed"
                            val run = getValidationRuns(vs.name, 1).firstOrNull()
                            assertNotNull(run, "Validation was done") {
                                assertEquals("FAILED", it.lastStatus.id)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Validation with build variable`() {
        project {
            branch {
                val vs = validationStamp()
                build("4.5.3") { // Hardcoded in MockingTFCClientFactory
                    // Payload
                    val payload =
                        resourceAsText("/tfc/run-completed-applied.json")
                            .parseAsJson()
                    // Sending the payload to the hook
                    withTfcHookEnabled {
                        withTfcConfiguration {
                            val response = sendPayloadToHook(
                                ref = this,
                                build = "@ontrack_version", // Hardcoded in MockingTFCClientFactory
                                validation = vs.name,
                                payload = payload,
                            )
                            // Gets the queue ID
                            val queueID = response.queueID
                            // Waiting for the processing to be don
                            val queueSupport = QueueACCTestSupport(ontrack)
                            queueSupport.waitForQueueRecordToBe(queueID, QueueRecordState.COMPLETED)
                            // Checks the build has been validated to "passed"
                            val run = getValidationRuns(vs.name, 1).firstOrNull()
                            assertNotNull(run, "Validation was done") {
                                assertEquals("PASSED", it.lastStatus.id)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Validation with build variable undefined`() {
        project {
            branch {
                val vs = validationStamp()
                build("4.5.3") { // Hardcoded in MockingTFCClientFactory
                    // Payload
                    val payload =
                        resourceAsText("/tfc/run-completed-applied.json")
                            .parseAsJson()
                    // Sending the payload to the hook
                    withTfcHookEnabled {
                        withTfcConfiguration {
                            val response = sendPayloadToHook(
                                ref = this,
                                build = "@unknown_variable", // Not managed by MockingTFCClientFactory
                                validation = vs.name,
                                payload = payload,
                            )
                            // Gets the queue ID
                            val queueID = response.queueID
                            // Waiting for the processing to be in error
                            val queueSupport = QueueACCTestSupport(ontrack)
                            queueSupport.waitForQueueRecordToBe(queueID, QueueRecordState.ERRORED)
                            // Checks the build has NOT been validated to "passed"
                            val run = getValidationRuns(vs.name, 1).firstOrNull()
                            assertNull(run, "Validation was NOT done")
                        }
                    }
                }
            }
        }
    }

    private fun withTfcConfiguration(code: () -> Unit) {
        val name = uid("tfc_")
        val config = TFCConfiguration(
            name = name,
            url = "https://app.terraform.io",
            token = "xxx"
        )
        try {
            ontrack.configurations.tfc.create(config)
            code()
        } finally {
            ontrack.configurations.tfc.delete(name)
        }
    }

    private fun withTfcHookEnabled(code: () -> Unit) {
        val old = ontrack.settings.tfc.get()
        try {
            ontrack.settings.tfc.set(
                TFCSettings(
                    enabled = true,
                    token = ontrack.connector.token ?: error("Token is required"),
                )
            )
            code()
        } finally {
            ontrack.settings.tfc.set(old)
        }
    }

    private fun sendPayloadToHook(
        ref: Build,
        project: String = ref.branch.project.name,
        branch: String = ref.branch.name,
        build: String = ref.name,
        validation: String,
        payload: JsonNode,
    ): HookResponse {
        val response = ontrack.connector.post(
            "/hook/secured/tfc?project=$project&branch=$branch&build=$build&validation=$validation",
            headers = mapOf(
                "X-TFE-Notification-Signature" to "signature-is-not-checked",
            ),
            body = payload,
        )
        // Payload: response checks
        assertEquals(200, response.statusCode)
        // Extracting the response
        return response.body.parse()
    }

}