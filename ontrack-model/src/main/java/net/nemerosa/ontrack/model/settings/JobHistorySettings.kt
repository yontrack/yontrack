package net.nemerosa.ontrack.model.settings

import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.json.DurationDeserializer
import net.nemerosa.ontrack.model.json.DurationSerializer
import java.time.Duration

@APIDescription("Jobs history settings")
data class JobHistorySettings(
    @APIDescription("Maximum time to keep the history of background jobs")
    @JsonDeserialize(using = DurationDeserializer::class)
    @JsonSerialize(using = DurationSerializer::class)
    val retention: Duration = DEFAULT_JOB_HISTORY_RETENTION,
) {
    companion object {
        val DEFAULT_JOB_HISTORY_RETENTION: Duration = Duration.ofDays(30)
    }
}