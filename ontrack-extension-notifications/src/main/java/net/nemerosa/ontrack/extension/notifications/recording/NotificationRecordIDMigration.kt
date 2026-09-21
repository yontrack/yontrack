package net.nemerosa.ontrack.extension.notifications.recording

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Component
import java.util.*

/**
 * Assigning IDs to the existing notification records.
 */
@Component
class NotificationRecordIDMigration(
    private val storageService: StorageService,
) : StartupService {

    override fun getName(): String = "Assigning IDs to existing notification records"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        storageService.updateAll(
            store = DefaultNotificationRecordingService.STORE,
            type = JsonNode::class
        ) { _, record ->
            if (record.has("id")) {
                // ID already filled in, skipping
                null
            } else {
                (record as ObjectNode).put("id", UUID.randomUUID().toString())
                record
            }
        }
    }
}