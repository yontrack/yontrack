package net.nemerosa.ontrack.kdsl.spec.extension.environments

import net.nemerosa.ontrack.kdsl.spec.Build
import java.time.LocalDateTime

/**
 * Starts a pipeline for this build in the given slot.
 *
 * @param dateTime Start of the pipeline, to backdate it. Defaults to now.
 */
fun Build.startPipeline(slot: Slot, dateTime: LocalDateTime? = null): SlotPipeline =
    slot.createPipeline(this, dateTime = dateTime)
