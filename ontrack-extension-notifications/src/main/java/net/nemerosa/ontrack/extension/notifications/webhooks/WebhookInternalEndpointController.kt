package net.nemerosa.ontrack.extension.notifications.webhooks

import tools.jackson.databind.JsonNode
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.exceptions.NotFoundException
import net.nemerosa.ontrack.model.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Internal endpoint for simulating a webhook.
 *
 * Should be used only for testing.
 */
@RestController
@Profile(RunProfile.DEV)
@RequestMapping("/extension/notifications/webhooks/internal")
class WebhookInternalEndpointController(
    private val securityService: SecurityService,
    private val webhookAdminService: WebhookAdminService,
    private val webhookExecutionService: WebhookExecutionService,
) {

    private val logger: Logger = LoggerFactory.getLogger(WebhookInternalEndpointController::class.java)

    /**
     * Logging the usage
     */
    @PostConstruct
    fun warning() {
        logger.warn("Using internal webhook - this must be disabled in production!")
    }

    /**
     * Storing the payloads in memory
     */
    private val payloads = mutableListOf<JsonWebhookPayload>()

    /**
     * Testing a webhook using the test payload
     */
    @PostMapping("test")
    fun test(@RequestBody wrapper: TestPayloadWrapper) {
        securityService.asAdmin {
            val webhook = webhookAdminService.findWebhookByName(wrapper.webhook) ?: throw WebhookNotFoundException(
                wrapper.webhook)
            webhookExecutionService.send(
                webhook = webhook,
                payload = WebhookPayload(
                    type = "test",
                    data = wrapper.payload,
                )
            )
        }
    }

    /**
     * The endpoint.
     */
    @PostMapping("")
    fun post(@RequestBody payload: JsonWebhookPayload): String {
        payloads += payload
        return when (payload.type) {
            "test" -> test(payload) ?: "OK"
            "ping" -> ping(payload.data)
            else -> "OK"
        }
    }

    private fun ping(data: JsonNode): String =
        mapOf("ping" to data).asJson().format()

    private fun test(payload: JsonWebhookPayload): String? {
        val test = payload.data.parseOrNull<TestPayload>() ?: return null
        val value = when (test.mode) {
            TestPayloadMode.OK -> test.content
            TestPayloadMode.NOT_FOUND -> throw TestPayloadNotFoundException(test.content)
            TestPayloadMode.INTERNAL_ERROR -> throw TestPayloadInternalException(test.content)
        }
        if (test.delayMs != null) {
            runBlocking {
                delay(test.delayMs)
            }
        }
        return value
    }

    /**
     * Gets the list of payloads
     */
    @GetMapping("payloads")
    fun payloads() = payloads.toList()

    /**
     * Json payload
     */
    data class JsonWebhookPayload(
        val uuid: UUID,
        val type: String,
        val data: JsonNode,
    )

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

    /**
     * Not found exception
     */
    class TestPayloadNotFoundException(message: String) : NotFoundException(message)

    /**
     * Internal exception
     */
    class TestPayloadInternalException(message: String) : BaseException(message)

}
