package net.nemerosa.ontrack.extension.notifications.webhooks

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

abstract class AbstractWebhookExchangeTestSupport : AbstractNotificationTestSupport() {

    @Autowired
    protected lateinit var webhookExchangeService: WebhookExchangeService

    protected fun store(
        webhook: String = uid("wh"),
        requestTime: LocalDateTime = Time.now(),
        requestType: String = "event",
        requestPayload: JsonNode = mapOf("name" to "value").asJson(),
        responseTime: LocalDateTime? = null,
        responseCode: Int = 200,
        responsePayload: JsonNode = "OK".asJson(),
    ): WebhookExchange = WebhookExchange(
        uuid = UUID.randomUUID(),
        webhook = webhook,
        request = WebhookRequest(
            timestamp = requestTime,
            type = requestType,
            payload = requestPayload.format(),
        ),
        response = WebhookResponse(
            timestamp = responseTime ?: (requestTime + Duration.ofSeconds(10)),
            code = responseCode,
            payload = responsePayload.format(),
        ),
        stack = null,
    ).apply {
        webhookExchangeService.store(this)
    }

}