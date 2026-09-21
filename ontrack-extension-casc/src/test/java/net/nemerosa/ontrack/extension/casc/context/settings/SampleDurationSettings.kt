package net.nemerosa.ontrack.extension.casc.context.settings

import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import net.nemerosa.ontrack.model.json.DurationDeserializer
import net.nemerosa.ontrack.model.json.DurationSerializer
import java.time.Duration

/**
 * Sample settings for supporting durations.
 */
class SampleDurationSettings(
    @JsonSerialize(using = DurationSerializer::class)
    @JsonDeserialize(using = DurationDeserializer::class)
    val duration: Duration,
)
