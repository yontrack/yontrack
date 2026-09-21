package net.nemerosa.ontrack.extension.hook.settings

import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.json.DurationDeserializer
import net.nemerosa.ontrack.model.json.DurationSerializer
import java.time.Duration

@APIDescription("Hook settings")
data class HookSettings(
    @APIDescription("Maximum time to keep hook records for non-running requests")
    @APILabel("Records retention")
    @JsonDeserialize(using = DurationDeserializer::class)
    @JsonSerialize(using = DurationSerializer::class)
    val recordRetentionDuration: Duration = DEFAULT_RECORD_RETENTION_DURATION,
    @APIDescription("Maximum time to keep queue records for all kinds of hook requests (counted _after_ the retention)")
    @APILabel("Records cleanup")
    @JsonDeserialize(using = DurationDeserializer::class)
    @JsonSerialize(using = DurationSerializer::class)
    val recordCleanupDuration: Duration = DEFAULT_RECORD_CLEANUP_DURATION,
) {
    companion object {

        /**
         * Default value for [HookSettings.recordRetentionDuration]
         */
        val DEFAULT_RECORD_RETENTION_DURATION: Duration = Duration.ofDays(14)

        /**
         * Default value for [HookSettings.recordCleanupDuration]
         */
        val DEFAULT_RECORD_CLEANUP_DURATION: Duration = Duration.ofDays(90)

    }
}
