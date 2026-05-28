package net.nemerosa.ontrack.extension.notifications.processing

import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Service

@Service
class NotificationProcessingResultServiceImpl(
    private val notificationRecordingService: NotificationRecordingService,
    private val notificationChannelRegistry: NotificationChannelRegistry,
    private val securityService: SecurityService,
) : NotificationProcessingResultService {

    override fun getActualizedResult(processingResult: NotificationProcessingResult<*>): NotificationProcessingResult<*>? {
        val recordId = processingResult.recordId
        val result = getActualizedResult(recordId) ?: return null
        return NotificationProcessingResult(recordId = recordId, result = result)
    }

    override fun getActualizedResult(recordId: String): NotificationResult<*>? {
        val record = securityService.asAdmin { notificationRecordingService.findRecordById(recordId) } ?: return null
        val channel = notificationChannelRegistry.findChannel(record.channel) ?: return null
        return channel.getNotificationResult(record)
    }

}