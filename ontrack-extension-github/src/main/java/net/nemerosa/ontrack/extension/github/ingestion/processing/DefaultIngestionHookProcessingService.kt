package net.nemerosa.ontrack.extension.github.ingestion.processing

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.github.ingestion.GitHubIngestionHookEventNotSupportedException
import net.nemerosa.ontrack.extension.github.ingestion.metrics.IngestionMetrics
import net.nemerosa.ontrack.extension.github.ingestion.metrics.increment
import net.nemerosa.ontrack.extension.github.ingestion.metrics.timeForPayload
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayload
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStorage
import net.nemerosa.ontrack.model.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DefaultIngestionHookProcessingService(
    private val meterRegistry: MeterRegistry,
    private val securityService: SecurityService,
    private val ingestionHookPayloadStorage: IngestionHookPayloadStorage,
    ingestionEventProcessors: List<IngestionEventProcessor>,
) : IngestionHookProcessingService {

    private val logger: Logger = LoggerFactory.getLogger(DefaultIngestionHookProcessingService::class.java)

    private val eventProcessors = ingestionEventProcessors.associateBy { it.event }

    override fun process(payload: IngestionHookPayload) {
        ingestionHookPayloadStorage.start(payload)
        meterRegistry.increment(payload, IngestionMetrics.Process.startedCount)
        securityService.asAdmin(IngestionHookPayload.SYSTEM_REASON) {
            try {
                meterRegistry.timeForPayload(payload, IngestionMetrics.Process.time) {
                    val outcome = doProcess(payload)
                    val metric = when (outcome.result) {
                        IngestionEventProcessingResult.PROCESSED -> IngestionMetrics.Process.successCount
                        IngestionEventProcessingResult.IGNORED -> IngestionMetrics.Process.ignoredCount
                    }
                    meterRegistry.increment(payload, metric)
                    ingestionHookPayloadStorage.finished(payload, outcome)
                }
            } catch (any: Throwable) {
                meterRegistry.increment(payload, IngestionMetrics.Process.errorCount)
                ingestionHookPayloadStorage.error(payload, any)
                logger.error(
                    "Error while ingesting data from GitHub: event: ${payload.gitHubEvent}, delivery: ${payload.gitHubDelivery}",
                    any
                )
            } finally {
                meterRegistry.increment(payload, IngestionMetrics.Process.finishedCount)
            }
        }
    }

    private fun doProcess(payload: IngestionHookPayload): IngestionEventProcessingResultDetails {
        // Gets the processor for this event
        val eventProcessor = eventProcessors[payload.gitHubEvent]
            ?: throw GitHubIngestionHookEventNotSupportedException(payload.gitHubEvent)
        // Delegates the processing
        return eventProcessor.process(payload)
    }

}