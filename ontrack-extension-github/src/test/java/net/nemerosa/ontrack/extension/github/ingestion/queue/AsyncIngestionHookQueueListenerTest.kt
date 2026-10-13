package net.nemerosa.ontrack.extension.github.ingestion.queue

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.nemerosa.ontrack.extension.github.ingestion.IngestionConfigProperties
import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStorage
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionHookProcessingService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.security.AuthenticationStorageService
import net.nemerosa.ontrack.model.support.ReadMemo
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpoint
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class AsyncIngestionHookQueueListenerTest {

    @Test
    fun `Each message is processed in a read memo of its own`() {
        val memos = mutableListOf<ReadMemo?>()
        val processingService = mockk<IngestionHookProcessingService>()
        every { processingService.process(any()) } answers { memos += ReadMemo.current() }
        val authenticationStorageService = mockk<AuthenticationStorageService>()
        every { authenticationStorageService.withAccountId(any(), any(), any()) } answers {
            thirdArg<() -> Unit>().invoke()
        }
        val listener = AsyncIngestionHookQueueListener(
            ingestionConfigProperties = IngestionConfigProperties(),
            ingestionHookProcessingService = processingService,
            ingestionHookPayloadStorage = mockk<IngestionHookPayloadStorage>(relaxed = true),
            meterRegistry = SimpleMeterRegistry(),
            authenticationStorageService = authenticationStorageService,
        )

        val endpoint = slot<RabbitListenerEndpoint>()
        val registrar = mockk<RabbitListenerEndpointRegistrar>()
        every { registrar.registerEndpoint(capture(endpoint)) } returns Unit
        listener.configureRabbitListeners(registrar)
        val messageListener = assertNotNull((endpoint.captured as SimpleRabbitListenerEndpoint).messageListener)

        repeat(2) {
            val body = IngestionHookFixtures.sampleWorkflowRunIngestionPayload().asJson().format()
            messageListener.onMessage(
                Message(
                    body.toByteArray(),
                    MessageProperties().apply { consumerQueue = "github.ingestion.default" },
                )
            )
        }

        assertEquals(2, memos.size, "Both messages processed")
        val (first, second) = memos
        assertNotNull(first, "First message processed in a memo")
        assertNotNull(second, "Second message processed in a memo")
        assertNotSame(first, second, "Each message has a memo of its own")
        assertNull(ReadMemo.current(), "Memo closed after the processing")
    }
}
