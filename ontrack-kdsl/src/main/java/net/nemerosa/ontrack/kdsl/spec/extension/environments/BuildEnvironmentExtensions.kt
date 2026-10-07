package net.nemerosa.ontrack.kdsl.spec.extension.environments

import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Readiness
import net.nemerosa.ontrack.kdsl.spec.queryReadiness
import java.time.LocalDateTime

/**
 * Starts a pipeline for this build in the given slot.
 *
 * @param dateTime Start of the pipeline, to backdate it. Defaults to now.
 */
fun Build.startPipeline(slot: Slot, dateTime: LocalDateTime? = null): SlotPipeline =
    slot.createPipeline(this, dateTime = dateTime)

/**
 * What this build still lacks to be deployed in the given slot of its project.
 */
fun Build.readiness(slot: Slot): Readiness = queryReadiness(promotionLevel = null, slotId = slot.id)
