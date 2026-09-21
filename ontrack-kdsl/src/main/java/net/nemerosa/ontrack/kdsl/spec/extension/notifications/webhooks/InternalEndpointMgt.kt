package net.nemerosa.ontrack.kdsl.spec.extension.notifications.webhooks

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector

/**
 * Interface for the test internal endpoint
 */
class InternalEndpointMgt(connector: Connector) : Connected(connector) {

    /**
     * Sends a test payload
     */
    fun test(testPayloadWrapper: TestPayloadWrapper) {
        connector.post("$PATH/test", body = testPayloadWrapper)
    }

    /**
     * Gets the list of received payloads
     */
    val payloads: List<InternalEndpointPayload>
        get() = connector.get("$PATH/payloads")
            .body.asJson().values().map {
                it.parse()
            }

    companion object {
        const val PATH = "/extension/notifications/webhooks/internal"
    }

}

/**
 * Test payload wrapper
 */
data class TestPayloadWrapper(
    val webhook: String,
    val payload: TestPayload,
)

/**
 * Test payload
 */
data class TestPayload(
    val mode: TestPayloadMode,
    val content: String,
    val delayMs: Long? = null,
)

/**
 * Test payload mode. How the internal endpoint must act.
 */
enum class TestPayloadMode {
    /**
     * Returning an OK answer, with the given content in the request.
     */
    OK,

    /**
     * Returning a not found error
     */
    NOT_FOUND,

    /**
     * Returning an internal error
     */
    INTERNAL_ERROR,
}
